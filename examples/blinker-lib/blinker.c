#include "blinker.h"

uint8_t blinker_initialize() {
  return true;
}

uint8_t blinker_blink(uint8_t pin) {
  delay(500);
  digitalWrite(pin, HIGH);
  delay(500);
  digitalWrite(pin, LOW);
}
