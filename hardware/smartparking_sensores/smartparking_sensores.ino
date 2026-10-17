// =====================================================================
// SmartParking - Firmware de sensores 
// ---------------------------------------------------------------------
// PROTOCOLO SERIAL (9600 baudios)

// Comandos que recibe (el salto de linea es opcional):
// R<n>  Reservar el cajon n (1-3)                          ej. R1
// C<n>  La persona que reservo ya llego (escaneo su QR)    ej. C1
// L<n>  Liberar la reserva del cajon n (1-3)               ej. L2
// F<n>  Cambiar el intervalo de envio a n segundos (1-5)   ej. F3

// Mensajes que envia: "Sensor <n>: <estado>" 

// ESTADOS
// 0 = libre       
// 1 = ocupado 
// 2 = reservado 
// =====================================================================


// Configuración de pines
const byte NUM_CAJONES = 3;

const byte TRIG[NUM_CAJONES] = {2, 4, 6};
const byte ECHO[NUM_CAJONES] = {3, 5, 7};

const byte LED_LIBRE[NUM_CAJONES]     = {8, 9, 10};
const byte LED_OCUPADO[NUM_CAJONES]   = {11, 12, 13};
const byte LED_RESERVADO[NUM_CAJONES] = {A0, A1, A2};

// Parametros
const int UMBRAL_CM = 10; // A menos de esta distancia el cajon pasa a OCUPADO
const int HISTERESIS_CM = 3;  // Para volver a LIBRE debe alejarse a más
const int DISTANCIA_MINIMA_CM = 2;  // Lecturas menores son ruido del sensor y se ignoran
const int DISTANCIA_MAXIMA_CM = 100; // Mas alla de esto (o sin eco) se considera que no hay nada
const byte LECTURAS_CONFIRMACION = 3; // Lecturas iguales seguidas para cambiar de estado
const unsigned long PAUSA_ENTRE_SENSORES_MS = 40; // Evita que el eco de un sensor llegue a otro
const unsigned long PERIODO_PARPADEO_MS = 250;  // Velocidad del parpadeo rojo
const unsigned long TIMEOUT_COMANDO_MS = 200; // Descarta un comando incompleto

// Tiempo maximo de espera del eco
const unsigned long TIMEOUT_ECO_US = (unsigned long)DISTANCIA_MAXIMA_CM * 2 * 1000000UL / 34300UL;

unsigned long intervaloEnvioMs = 2000;  // envio periodico (1-5 s, cambia con F<n>)

// Estado de cada cajon
const byte LIBRE = 0, OCUPADO = 1, RESERVADO = 2;

byte estado[NUM_CAJONES]              = {LIBRE, LIBRE, LIBRE};
bool reservaActiva[NUM_CAJONES]       = {false, false, false};
bool presenciaConfirmada[NUM_CAJONES] = {false, false, false};
byte contadorCambio[NUM_CAJONES]      = {0, 0, 0};
bool llegadaConfirmada[NUM_CAJONES]   = {false, false, false};  // el que reservo ya escaneo su QR

byte sensorActual = 0;
unsigned long ultimaMedicion = 0;
unsigned long ultimoEnvioPeriodico = 0;

char comandoPendiente = 0;
unsigned long tiempoComando = 0;

// Prototipos
int  medirDistancia(byte trigPin, byte echoPin);
void procesarLectura(byte i, int distancia);
void actualizarEstado(byte i);
void enviarEstado(byte i);
void actualizarLeds(byte i);
void leerComandos();
void ejecutarComando(char comando, char valor);

// SETUP
void setup() {
  Serial.begin(9600);

  for (byte i = 0; i < NUM_CAJONES; i++) {
    pinMode(TRIG[i], OUTPUT);
    digitalWrite(TRIG[i], LOW);
    pinMode(ECHO[i], INPUT);

    pinMode(LED_LIBRE[i], OUTPUT);
    pinMode(LED_OCUPADO[i], OUTPUT);
    pinMode(LED_RESERVADO[i], OUTPUT);

    actualizarLeds(i);  // arranca con el LED de libre encendido
    enviarEstado(i);  // informa el estado inicial
  }
}

// LOOP sin delay
void loop() {
  leerComandos();

  // Se mide un sensor a la vez, con una pausa entre cada uno
  if (millis() - ultimaMedicion >= PAUSA_ENTRE_SENSORES_MS) {
    ultimaMedicion = millis();
    procesarLectura(sensorActual, medirDistancia(TRIG[sensorActual], ECHO[sensorActual]));
    sensorActual = (sensorActual + 1) % NUM_CAJONES;
  }

  for (byte i = 0; i < NUM_CAJONES; i++) {
    actualizarEstado(i);
    actualizarLeds(i);
  }

  // Envio periodico del estado de todos los cajones
  if (millis() - ultimoEnvioPeriodico >= intervaloEnvioMs) {
    ultimoEnvioPeriodico = millis();
    for (byte i = 0; i < NUM_CAJONES; i++) {
      enviarEstado(i);
    }
  }
}

// Medicion
// Devuelve la distancia en cm, o 0 si no hubo eco
int medirDistancia(byte trigPin, byte echoPin) {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);

  unsigned long duracion = pulseIn(echoPin, HIGH, TIMEOUT_ECO_US);
  return (int)(duracion * 0.0343 / 2);
}

// Decide si hay un objeto y aplica el filtro de lecturas seguidas
void procesarLectura(byte i, int distancia) {
  // Quitar ruido de los sensores
  if (distancia > 0 && distancia < DISTANCIA_MINIMA_CM) return;

  // Sin eco (0) = no hay nada enfrente = libre.
  // Una vez ocupado, debe alejarse un poco mas para volver a libre
  int limite = presenciaConfirmada[i] ? UMBRAL_CM + HISTERESIS_CM : UMBRAL_CM;
  bool hayObjeto = (distancia > 0 && distancia < limite);

  if (hayObjeto == presenciaConfirmada[i]) {
    contadorCambio[i] = 0;
    return;
  }

  if (++contadorCambio[i] < LECTURAS_CONFIRMACION) return;

  // Cambio confirmado
  presenciaConfirmada[i] = hayObjeto;
  contadorCambio[i] = 0;
}

// Estado
void actualizarEstado(byte i) {
  byte nuevo;
  if (presenciaConfirmada[i]) {
    nuevo = OCUPADO;
  } else if (reservaActiva[i]) {
    nuevo = RESERVADO;
  } else {
    nuevo = LIBRE;
  }

  if (nuevo != estado[i]) {
    estado[i] = nuevo;
    enviarEstado(i);
  }
}

void enviarEstado(byte i) {
  Serial.print(F("Sensor "));
  Serial.print(i + 1);
  Serial.print(F(": "));
  Serial.println(estado[i]);
}

// LEDS
void actualizarLeds(byte i) {
  bool rojo = false;

  if (estado[i] == OCUPADO) {
    // Cajon reservado ocupado por alguien que no ha confirmado su llegada va a parpadear
    bool ocupadoSinConfirmar = reservaActiva[i] && !llegadaConfirmada[i];
    rojo = ocupadoSinConfirmar ? (millis() / PERIODO_PARPADEO_MS) % 2 : true;
  }

  digitalWrite(LED_LIBRE[i],     estado[i] == LIBRE     ? HIGH : LOW);
  digitalWrite(LED_OCUPADO[i],   rojo                   ? HIGH : LOW);
  digitalWrite(LED_RESERVADO[i], estado[i] == RESERVADO ? HIGH : LOW);
}

// Comandos seriales (sin bloquear)
void leerComandos() {
  while (Serial.available()) {
    char c = Serial.read();
    if (c == '\n' || c == '\r' || c == ' ') continue;
    if (c >= 'a' && c <= 'z') c -= 32;   // acepta r1, l1, f2 en minusculas

    if (comandoPendiente == 0) {
      if (c == 'R' || c == 'C' || c == 'L' || c == 'F') {
        comandoPendiente = c;
        tiempoComando = millis();
      }
    } else {
      ejecutarComando(comandoPendiente, c);
      comandoPendiente = 0;
    }
  }

  // Si llego una letra sin numero, se descarta despues de un tiempo
  if (comandoPendiente != 0 && millis() - tiempoComando > TIMEOUT_COMANDO_MS) {
    comandoPendiente = 0;
  }
}

void ejecutarComando(char comando, char valor) {
  int n = valor - '0';

  if (comando == 'R' && n >= 1 && n <= NUM_CAJONES) {
    reservaActiva[n - 1] = true;
    llegadaConfirmada[n - 1] = false;
  } else if (comando == 'C' && n >= 1 && n <= NUM_CAJONES) {
    if (reservaActiva[n - 1]) llegadaConfirmada[n - 1] = true;
  } else if (comando == 'L' && n >= 1 && n <= NUM_CAJONES) {
    reservaActiva[n - 1] = false;
    llegadaConfirmada[n - 1] = false;
  } else if (comando == 'F' && n >= 1 && n <= 5) {
    intervaloEnvioMs = (unsigned long)n * 1000;
  }
}
