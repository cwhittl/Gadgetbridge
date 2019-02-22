var util = require('util');

var bleno = require('bleno-mac');

var BlenoPrimaryService = bleno.PrimaryService;

var NotificiationCharacteristic = require('./characteristics/notification');
var MessageSyncCharacteristic = require('./characteristics/messageSync');

function GadgetBridgeService() {
  GadgetBridgeService.super_.call(this, {
    uuid: '13333333-3333-3333-3333-800000000000',
    characteristics: [
      new NotificiationCharacteristic(),
      new MessageSyncCharacteristic()
    ]
  });
}

util.inherits(GadgetBridgeService, BlenoPrimaryService);

module.exports = GadgetBridgeService;