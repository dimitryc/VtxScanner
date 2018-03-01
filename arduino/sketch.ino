#define spiDataPin 10
#define slaveSelectPin 11
#define spiClockPin 12

#define portOfPin(P)\
  (((P)>=0&&(P)<8)?&PORTD:(((P)>7&&(P)<14)?&PORTB:&PORTC))
#define ddrOfPin(P)\
  (((P)>=0&&(P)<8)?&DDRD:(((P)>7&&(P)<14)?&DDRB:&DDRC))
#define pinOfPin(P)\
  (((P)>=0&&(P)<8)?&PIND:(((P)>7&&(P)<14)?&PINB:&PINC))
#define pinIndex(P)((uint8_t)(P>13?P-14:P&7))
#define pinMask(P)((uint8_t)(1<<pinIndex(P)))

#define pinAsInput(P) *(ddrOfPin(P))&=~pinMask(P)
#define pinAsInputPullUp(P) *(ddrOfPin(P))&=~pinMask(P);digitalHigh(P)
#define pinAsOutput(P) *(ddrOfPin(P))|=pinMask(P)
#define digitalLow(P) *(portOfPin(P))&=~pinMask(P)
#define digitalHigh(P) *(portOfPin(P))|=pinMask(P)
#define digitalToggle(P) *(portOfPin(P))^=pinMask(P)
#define isHigh(P)((*(pinOfPin(P))& pinMask(P))>0)
#define isLow(P)((*(pinOfPin(P))& pinMask(P))==0)
#define digitalState(P)((uint8_t)isHigh(P))

// rx5808 module needs >30ms to tune.
#define MIN_TUNE_TIME 30

void setupSPIpins() {
    // SPI pins for RX control
    pinMode (slaveSelectPin, OUTPUT);
    pinMode (spiDataPin, OUTPUT);
    pinMode (spiClockPin, OUTPUT);
}

void SERIAL_SENDBIT1() {
    digitalLow(spiClockPin);
    delayMicroseconds(1);

    digitalHigh(spiDataPin);
    delayMicroseconds(1);
    digitalHigh(spiClockPin);
    delayMicroseconds(1);

    digitalLow(spiClockPin);
    delayMicroseconds(1);
}

void SERIAL_SENDBIT0() {
    digitalLow(spiClockPin);
    delayMicroseconds(1);

    digitalLow(spiDataPin);
    delayMicroseconds(1);
    digitalHigh(spiClockPin);
    delayMicroseconds(1);

    digitalLow(spiClockPin);
    delayMicroseconds(1);
}

void SERIAL_ENABLE_LOW() {
    delayMicroseconds(1);
    digitalLow(slaveSelectPin);
    delayMicroseconds(1);
}

void SERIAL_ENABLE_HIGH() {
    delayMicroseconds(1);
    digitalHigh(slaveSelectPin);
    delayMicroseconds(1);
}

uint16_t setModuleFrequency(uint16_t frequency) {
    uint8_t i;
    uint16_t channelData;

    channelData = frequency - 479;
    channelData /= 2;
    i = channelData % 32;
    channelData /= 32;
    channelData = (channelData << 7) + i;

    // bit bang out 25 bits of data
    // Order: A0-3, !R/W, D0-D19
    // A0=0, A1=0, A2=0, A3=1, RW=0, D0-19=0
    cli();
    SERIAL_ENABLE_HIGH();
    delayMicroseconds(1);
    SERIAL_ENABLE_LOW();

    SERIAL_SENDBIT0();
    SERIAL_SENDBIT0();
    SERIAL_SENDBIT0();
    SERIAL_SENDBIT1();

    SERIAL_SENDBIT0();

    // remaining zeros
    for (i = 20; i > 0; i--) {
        SERIAL_SENDBIT0();
    }

    // Clock the data in
    SERIAL_ENABLE_HIGH();
    delayMicroseconds(1);
    SERIAL_ENABLE_LOW();

    // Second is the channel data from the lookup table
    // 20 bytes of register data are sent, but the MSB 4 bits are zeros
    // register address = 0x1, write, data0-15=channelData data15-19=0x0
    SERIAL_ENABLE_HIGH();
    SERIAL_ENABLE_LOW();

    // Register 0x1
    SERIAL_SENDBIT1();
    SERIAL_SENDBIT0();
    SERIAL_SENDBIT0();
    SERIAL_SENDBIT0();

    // Write to register
    SERIAL_SENDBIT1();

    // D0-D15
    //   note: loop runs backwards as more efficent on AVR
    for (i = 16; i > 0; i--) {
        // Is bit high or low?
        if (channelData & 0x1) {
            SERIAL_SENDBIT1();
        }
        else {
            SERIAL_SENDBIT0();
        }
        // Shift bits along to check the next one
        channelData >>= 1;
    }

    // Remaining D16-D19
    for (i = 4; i > 0; i--) {
        SERIAL_SENDBIT0();
    }

    // Finished clocking data in
    SERIAL_ENABLE_HIGH();
    delayMicroseconds(1);

    digitalLow(slaveSelectPin);
    digitalLow(spiClockPin);
    digitalLow(spiDataPin);
    sei();
    
    delay(MIN_TUNE_TIME);
    
    return frequency;
}

const int rssiPin = 3;

void setup() {
  // put your setup code here, to run once:
  Serial.begin(115200);

  setupSPIpins();
  delay(MIN_TUNE_TIME);
}

int measureRssi(uint16_t freq) {
  setModuleFrequency(freq);  
  return analogRead(rssiPin);
}

String data;

void loop() {
  // put your main code here, to run repeatedly:
  while (Serial.available() > 0) {
    char received = Serial.read();
    data += received;

    if (received == '\n') {
      long freq = data.toInt();
      int rssiValue = measureRssi(freq);
      Serial.print(freq);
      Serial.print(" ");
      Serial.println(rssiValue);
      data = "";
    }
  }
}

