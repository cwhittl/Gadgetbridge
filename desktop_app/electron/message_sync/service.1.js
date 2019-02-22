var util = require('util');

var bleno = require('bleno-mac');

var BlenoPrimaryService = bleno.PrimaryService;

var MessageSyncCharacteristic = require('./characteristic');

function MessageSyncService() {
  MessageSyncService.super_.call(this, {
    uuid: '13333333-3333-3333-3333-800000000000',
    characteristics: [
      new MessageSyncCharacteristic()
    ]
  });
}

util.inherits(MessageSyncService, BlenoPrimaryService);

module.exports = MessageSyncService;