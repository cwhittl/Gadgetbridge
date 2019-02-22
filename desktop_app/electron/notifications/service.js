var util = require('util');

var bleno = require('bleno-mac');

var BlenoPrimaryService = bleno.PrimaryService;

var NotificationCharacteristic = require('./characteristic');

function NotificationService() {
  NotificationService.super_.call(this, {
    uuid: '13333333-3333-3333-3333-700000000000',
    characteristics: [
      new NotificationCharacteristic()
    ]
  });
}

util.inherits(NotificationService, BlenoPrimaryService);

module.exports = NotificationService;