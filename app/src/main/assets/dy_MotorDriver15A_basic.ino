  /*
   * Arduino MEGA ADK controlled from Android Application 
   *   Created on: 8.04.2017
   *   by DubyOriginal
   *  
   * ---------------------------------------------------------  
   * -> Arduino MEGA ADK
   * -> Android
   * -> Motor driver: H-Bridge 15A 
   * -> ACS712 -> Current Sensor Module 
   * 
   * --------------------------------------------------------- 
   * source: Battery
   * -> Arduino MEGA ADK 5V -> Motor driver: H-Bridge 15A
   * 
   * ---------------------------------------------------------
   * Programmer setup:
   *    - Tools -> Board -> Arduino MEGA ADK
   */

#include <Max3421e.h>
#include <Usb.h>
#include <AndroidAccessory.h>

//*********************************************************************************************************
#define CMD_MOTOR 0x8
#define CMD_CURRENT 0x7
#define LMOTOR 0x1
#define RMOTOR 0x2

// CONST
//---------------------------------------------------------------
#define L_DIR 2
#define L_PWM 3
#define R_DIR 4
#define R_PWM 5
#define BOARD_LED 13
#define AN_CURRENT A0      //ACS712 -> Current Sensor Module 

//VAR
//---------------------------------------------------------------
byte rcvmsg[3];   //3 byte command

//OTHER
//---------------------------------------------------------------
#define serial Serial
AndroidAccessory acc(
         "Google, Inc.",
         "DemoKit",
         "DemoKit Arduino Board",
         "1.0",
         "http://www.android.com",
         "0000000012345678"
         );


//*********************************************************************************************************
void setup() {
  serial.begin(115200);
  serial.println("");
  serial.println("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
  serial.println("Motor Driver v1");
  serial.println("starting...");
 
  preparePINS();
  initialBlink();

  acc.powerOn();
  serial.println("ready");
}

//*********************************************************************************************************
void preparePINS(){
    pinMode(BOARD_LED, OUTPUT);

    pinMode(L_DIR, OUTPUT);
    pinMode(L_PWM, OUTPUT);
    pinMode(R_DIR, OUTPUT);
    pinMode(R_PWM, OUTPUT);

    analogWrite(L_PWM, 0);
    analogWrite(R_PWM, 0);
}

void initialBlink(){
    digitalWrite(BOARD_LED, 1);
    delay(700);
    digitalWrite(BOARD_LED, 0);
    delay(700);
    digitalWrite(BOARD_LED, 1);
    delay(700);
    digitalWrite(BOARD_LED, 0);
}

//*********************************************************************************************************
void loop() {
  if (acc.isConnected()) {
    int len = acc.read(rcvmsg, sizeof(rcvmsg), 1);
    if (len > 0) {
      if (rcvmsg[0] == CMD_MOTOR) {
        int mSpeedRel = rcvmsg[2] & 0xFF;  //0-100%
        int mSpeed = map(mSpeedRel, 0, 100, 0, 255);
        if(rcvmsg[1] == LMOTOR) {
          serial.println("CMD LEFT MOTOR - " + String(mSpeedRel));
          analogWrite(L_PWM, mSpeed);
        }else if(rcvmsg[1] == RMOTOR) {
          serial.println("CMD RIGHT MOTOR - " + String(mSpeedRel));
          analogWrite(R_PWM, mSpeed);
        }
      }else if (rcvmsg[0] == CMD_CURRENT){
        serial.println("CMD_CURRENT");
        if(rcvmsg[1] == LMOTOR) {
          int rawADCValue = analogRead(AN_CURRENT);
          serial.println("LMOTOR_CURRENT: " + String(rawADCValue));
          byte sntmsg[6];
          sntmsg[0] = CMD_CURRENT;
          sntmsg[1] = LMOTOR;
          sntmsg[2] = (byte) (rawADCValue >> 24);
          sntmsg[3] = (byte) (rawADCValue >> 16);
          sntmsg[4] = (byte) (rawADCValue >> 8);
          sntmsg[5] = (byte) rawADCValue;
          acc.write(sntmsg, 6);
          delay(100);
        }
      }
    }
  }else{
    serial.println("aac not connected!");
    delay(400);
        //set the accessory to its default state
  }

}
