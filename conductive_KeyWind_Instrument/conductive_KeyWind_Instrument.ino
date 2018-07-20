/* CY8CMBR3110 Register Map Offset Address */
#define BUTTON_STATUS 0xaa
#define CTRL_CMD 0x86
#define REGMAP_ORIGIN 0x00
#define SLAVE_ADDR 0x37
#define DIFF_COUNT_S0 0xBA
#define DIFF_COUNT_S1 0xBC

/* Command Codes */
#define LOAD_FACTORY 0x04
#define SAVE_CHECK_CRC 0x02
#define SW_RESET 0xFF

#include <Wire.h>

#include <Adafruit_MMA8451.h>
#include <Adafruit_Sensor.h>
#include <ESP8266WiFi.h>

Adafruit_MMA8451 mma = Adafruit_MMA8451();

const int BlueLedPin=2;

//cap sense
char b[2]={'0','0'}; //button satus bytes
unsigned char p[2]={'0','0'};
unsigned int p0=0; //difference count for sensor 0 (proximity sensor)

//acceletometer
const int H = 12;
boolean accel[H]={true};
int idx=0;

//microphone
const int sampleWindow = 5; // Sample window width in mS (50 mS = 20Hz)
unsigned int sample;
const float W = 10;
float tiny=1-(1/W);

//message for Android
String MSG="000000000000";
boolean buttons[8]={false};

//false->keyboard | true->sax
boolean instrument=false;
int volume=90; //default volume

//wifi connection
char ssid[] = "";      // your network SSID
char pass[] = ""; // your network password
//int keyIndex = 0;                 // your network key Index number (needed only for WEP)

// How often to ping (milliseconds)
static const int PING_TIME = 1000;

// Special messages
static const String PING_MSG = "SOCKET_PING";
static const String CONNECTED_MSG = "SOCKET_CONNECTED";

WiFiServer server(80);

WiFiClient client = server.available();   // listen for incoming clients


//*************** SETUP ********************************************************
void setup() { 
  
  Wire.begin(); //join the I2C bus as MASTER
  //ACCELEROMETER 
  // !!! IMPORTANT: comment Wire.begin() in the "begin" method of Adafruit_MMA8451.cpp !!!
  if (! mma.begin()) {
    Serial.println("Couldnt start");
    while(1);
  } 
  mma.setRange(MMA8451_RANGE_2_G);
  
  //Set led pins as outputs
  pinMode(BlueLedPin, OUTPUT);
  digitalWrite(BlueLedPin,HIGH); 
  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN,HIGH); 

  //attachInterrupt(0, HostInterrruptISR, RISING);
  //start serial com
  Serial.begin(9600); 
  delay(5000);
  
  //load the configuration for Cypress CapSense
  configureCY8CMBR3110();
  
  
  delay(1000);
  WiFi.begin(ssid, pass);
  server.begin();  
  delay(2000);
    
  Serial.println("Joining network " + String(ssid) + "...");
  if (!WiFi.begin(ssid, pass, true)) {
    
    Serial.println("ERROR: Failed to connect!");
    while (1) {}
  }
  
  server.begin();

  delay(5000);

  Serial.print("IP address: ");
  Serial.println(WiFi.localIP()); 
  Serial.println("WiFi server initialized!");
  
  printWiFiStatus();

}

//*************** LOOP ********************************************************
void loop()
{
  GoWiFi();  
}

//*****************************************************************************
void GoWiFi() {
  
   // Looks for a connected client
  WiFiClient client = server.available();
  
  if (client) {
  
    // Last time a ping was sent
    int senttime = millis();

    Serial.println("Connected to client.");
    client.println(CONNECTED_MSG);
    
    while (client.connected()) {
       
       ReadCapSense();
       ReadAccelerometerZ();
       if(instrument)
       {
        ReadMic();       
       }
       else
       {
        volume=90;
       }
       
       send_message(client);
             
      // Send regular ping messages to keep the connection open
      int now = millis();
      if (now - senttime > PING_TIME) {
        client.println(PING_MSG);
        senttime = now;
      }
    }
    
    // Give the client time to receive data before closing
    Serial.println("Disconnecting from client.");
    delay(100);
    client.flush();
    client.stop();
  }
}

//*****************************************************************************
void ReadAccelerometerZ()
{
  //READ ACCELEROMETER
   mma.read();
     
   //hysteresis cycle to avoid abrupt oscillations in "instrument" value
   if(idx==H){idx=0;}     
   
   if(mma.z>3800){
    accel[idx]=true;
    }
    
   else {
    accel[idx]=false;
    }
    
    idx++;

    int k=0;
    for(int j=0;j<H;j++)
      {
      if(accel[j]==true)
      {k++;} 
      }

    if(k==H){instrument=false;}
    else if (k==0) {instrument=true;}
    
}

//*****************************************************************************
void ReadMic() {

  unsigned long startMillis= millis();  // Start of sample window
  unsigned int peakToPeak = 0;   // peak-to-peak level
  unsigned int signalMax = 0;
  unsigned int signalMin = 1024;
   
  //calc peak to peak value
  while (millis() - startMillis < sampleWindow)
   {
     sample = analogRead(0);
       
      if (sample < 1024)  // toss out spurious readings
      {
         if (sample > signalMax)
         {
            signalMax = sample;  // save just the max levels
         }
         else if (sample < signalMin)
         {
            signalMin = sample;  // save just the min levels
         }
      }
   }
   
  peakToPeak = signalMax - signalMin;  // max - min = peak-peak amplitude
  double volts = (peakToPeak * 3.3) / 1024;  // convert to volts
  
  volts=map_float(volts,0,2.91,0,127); //remap in the correct range
  
  //SMOOTHING METHOD: exponentially decaying moving average
  volume=tiny*volume+(1-tiny)*volts;

}

//*****************************************************************************
void ReadCapSense() //Interrupt service routine

{
  //READ BUTTON STATUS
  Wire.beginTransmission(SLAVE_ADDR); // transmit to device #0x37
  Wire.write(BUTTON_STATUS);         // sends one byte 
  Wire.endTransmission();    // stop transmitting

  // request 2 bytes from slave device SLAVE_ADDR
  Wire.requestFrom(SLAVE_ADDR,2);
  
  int i=0;
  while (Wire.available()) {
    
  b[i] = Wire.read(); // receive a byte as character
  i++;
  }
  Wire.endTransmission();    // stop transmitting  
  //print the byte obtained from the 0xaa register
  //if(av!=0){printByte(b);}
  
  //----------- check buttons  -------------------- 
  //button CS5 - PIN 9 - FIRST BYTE SIXTH BIT
  if((b[0] & 0x20) != 0)
  {    
    //FIRST NOTE
    buttons[7]=true;
  }
  else {buttons[7]=false;} 
  //----------------------------------------------- 
  //button CS6 - PIN 10 - FIRST BYTE SEVENTH BIT
  if((b[0] & 0x40) != 0)
  {    
    //SECOND NOTE
    buttons[6]=true;
  }
  else {buttons[6]=false;}
  //----------------------------------------------- 
  //button CS7 - PIN 11 - FIRST BYTE EIGHTH BIT
  if((b[0] & 0x80) != 0)
  {    
    //THIRD NOTE
    buttons[5]=true;
  }
  else {buttons[5]=false;}
  //-----------------------------------------------
  //button CS8 - PIN 12 - SECOND BYTE FIRST BIT
  if((b[1] & 0x01) != 0)
  {    
    //FOURTH NOTE
    buttons[4]=true;
   }
   else{
    buttons[4]=false;
    }
  //-----------------------------------------------
  //button CS2 - PIN 13 - FIRST BYTE THIRD BIT
  if((b[0] & 0x04) != 0)
  {    
    //FIFTH NOTE
    buttons[3]=true;
  }
  else {buttons[3]=false;}
  //-----------------------------------------------
  //button CS9 - PIN 14 - SECOND BYTE SECOND BIT
  if((b[1] & 0x02) != 0)
  {    
    //SIXTH NOTE
    buttons[2]=true;
  }
  else {buttons[2]=false;}
  //-----------------------------------------------
  //button CS3 - PIN 15 - FIRST BYTE FOURTH BIT
  if((b[0] & 0x08) != 0)
  {    
    //SEVENTH NOTE
    buttons[1]=true;
  }   
  else {buttons[1]=false;}
  //-----------------------------------------------
  //button CS4 - PIN 16 - FIRST BYTE FIFTH BIT
  if((b[0] & 0x10) != 0)
  {    
    //EIGHTH NOTE
    buttons[0]=true;
  }
  else {buttons[0]=false;}

  
  //READ PROXIMITY DIFFERENCE COUNT FOR BUTTON 0
  Wire.beginTransmission(SLAVE_ADDR); // transmit to device #0x37
  Wire.write(DIFF_COUNT_S0);         // sends one byte 
  Wire.endTransmission();    // stop transmitting

  // request 2 bytes from slave device SLAVE_ADDR
  Wire.requestFrom(SLAVE_ADDR,2);
  int j=0;
  while (Wire.available()) 
  {  
    
    p[j] = Wire.read(); // receive a byte as character     
    j++;
    
  }
  Wire.endTransmission(); // stop transmitting 

  p0=BitShiftCombine(p[1],p[0]);
  if(p0>10000){p0=10000;} //clip
  p0=map(p0,0,10000,8192,16383); //remap
  if(p0<9500){p0=8192;} //avoid pitch oscillations due to other gestures
  delay(10);
  
}


//*****************************************************************************
void configureCY8CMBR3110(){

  //onfiguration array
  unsigned char configData[128] = {
  //GPO drive mode: Open Drain Low 
  //PS0 proximity enabled | all other buttons enabled (except CS1) | FSS disabled | auto-reset enabled to 20 sec
  //EMC enabled | Buttons sensitivity 400 fF (minimum sensitivity) | Buttons Finger Threshold 200
  0xFD, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xF0, 0xFF, 0x0F, 0x00, 0xC8, 0x7F, 0xC8, 0xC8, 0xC8, 0xC8, 0xC8, 
  0xC8, 0xC8, 0xC8, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 
  0x01, 0x80, 0x05, 0x00, 0x3E, 0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x1E, 0x00, 0x00, 0x00, 
  0x1E, 0x00, 0x00, 0x00, 0x00, 0x01, 0x01, 0x00, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 
  0x11, 0x03, 0x01, 0xA4, 0x00, 0x37, 0x06, 0x00, 0x00, 0x0A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 
  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 
  0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x8A, 0xE9

};

//2 Dummy Writes to Wake up the device
Wire.beginTransmission(SLAVE_ADDR); // transmit to device #0x37
Wire.write(REGMAP_ORIGIN);          // sends Offset byte 
Wire.write(00);
Wire.endTransmission();    // stop transmitting

Serial.print("CAPSENSE SETUP IN CORSO\n");

Wire.beginTransmission(SLAVE_ADDR); // transmit to device #0x37
Wire.write(REGMAP_ORIGIN);          // sends Offset byte 
Wire.write(00);
Wire.endTransmission();    // stop transmitting

//Arduino function can send only 32 bytes at a time hence the ConfigData is sent to CYCMBR in chunks
Wire.beginTransmission(SLAVE_ADDR); // transmit to device #0x37
Wire.write(REGMAP_ORIGIN);          // sends Offset byte 
Wire.write(&configData[0],31);        // sends 31 bytes (from 0 to 31)
Wire.endTransmission();    // stop transmitting

Wire.beginTransmission(SLAVE_ADDR); // transmit to device #0x37
Wire.write(31);          // sends Offset byte 
Wire.write(&configData[31],31);        // sends next 31 bytes (from 31 to 62)
Wire.endTransmission();    // stop transmitting

Wire.beginTransmission(SLAVE_ADDR); // transmit to device #0x37
Wire.write(62);          // sends Offset byte 
Wire.write(&configData[62],31);        // sends further 31 bytes (from 62 to 93)
Wire.endTransmission();    // stop transmitting

Wire.beginTransmission(SLAVE_ADDR); // transmit to device #0x37 
Wire.write(93);          // sends Offset byte 
Wire.write(&configData[93],31);        // sends 31 bytes (from 91 to 124)
Wire.endTransmission();    // stop transmitting

Wire.beginTransmission(SLAVE_ADDR); // transmit to device #0x37 
Wire.write(124);          // sends Offset byte 
Wire.write(&configData[124],4);        // sends last remaining 4 bytes
Wire.endTransmission();    // stop transmitting

//write configuration into non volatile memory
Wire.beginTransmission(SLAVE_ADDR); // transmit to device #0x37
Wire.write(CTRL_CMD);
Wire.write(SAVE_CHECK_CRC);
Wire.endTransmission();    // stop transmitting

delay(200);

//reet the device to apply the configuration
Wire.beginTransmission(SLAVE_ADDR); // transmit to device #0x37
Wire.write(CTRL_CMD);
Wire.write(SW_RESET);
Wire.endTransmission();    // stop transmitting

//Provide a delay to calculate and Save CRC 
delay(200);
  
}


// UTILITY FUNCTIONS
//
// ************************************************************************
void printByte(char b)
{
  int j=0;
  for (j = 0; j < 8; j++) {
    
      Serial.print(!!((b << j) & 0x80)); 
      }
  
  Serial.print("\n");
  
  }

// ************************************************************************
void blinkledfastBLUE()
 {
   digitalWrite(BlueLedPin, LOW);   // Turn the LED on (Note that LOW is the voltage level
                                    // but actually the LED is on; this is because it is acive low on the ESP-01)
   delay(200);                      // wait
   digitalWrite(BlueLedPin, HIGH);  // Turn the LED off by making the voltage HIGH
   delay(200);                      // wait
 }


// ************************************************************************
 void blinkledfastRED()
 {
   digitalWrite(LED_BUILTIN, LOW);   // Turn the LED on (Note that LOW is the voltage level
                                     // but actually the LED is on; this is because it is acive low on the ESP-01)
   delay(200);                       // wait
   digitalWrite(LED_BUILTIN, HIGH);  // Turn the LED off by making the voltage HIGH
   delay(200);                       // wait
 }

// ************************************************************************
 void printWiFiStatus() {
  Serial.println("");
  Serial.print("Connected to ");
  Serial.println(ssid);
  Serial.print("IP address: ");
  Serial.println(WiFi.localIP());
}

// ************************************************************************
/**
 * Called whenever a newline-delimited message is received.
 */
void got_message(String msg) {
  
  Serial.println("[RX] " + msg);
}

// ************************************************************************
/**
 * Send a message to the given client.
 */
void send_message(WiFiClient client) {
  
  //msg is updated continously, only when msg change must be send
  //format message for arduino midi converter

  
  String first;
  for(int i=0;i<8;i++)
    {
     first+=String(buttons[i]);
    }
  String second = String(instrument);
  char third[3];
  sprintf(third,"%03d",volume);  
  //char fourth[5];
  //sprintf(fourth,"%05d",p0);
  String fourth = String(p0);
  //concatenate string to send
  MSG=first+second+third+fourth;
  client.println(MSG);
  //Serial.println("[TX] " + MSG);

}

// ************************************************************************
char BoolToChar(bool b)
{
  return b ? '1' : '0';
}

// ************************************************************************
int map_float(float x, float in_min, float in_max, int out_min, int out_max)
{
  return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
}

// ************************************************************************
unsigned int BitShiftCombine(unsigned char x_high, unsigned char x_low)
{
  unsigned int combined; 
  combined = x_high;           //send x_high to rightmost 8 bits
  combined = combined<<8;      //shift x_high over to leftmost 8 bits
  combined |= x_low;           //logical OR keeps x_high intact in combined and fills in rightmost 8 bits
  return combined;
}
