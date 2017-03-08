#include <Arduino.h>

extern "C" {

void setup() {
    pinMode(13, OUTPUT);
}

void loop() {
    delay(500);
    digitalWrite(13, HIGH);
    delay(500);
    digitalWrite(13, LOW);
}

}
