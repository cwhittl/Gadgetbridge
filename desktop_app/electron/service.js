var util = require('util');

var bleno = require('bleno-mac');

var NotificiationCharacteristic = require('./characteristics/notification');
var MessageSyncCharacteristic = require('./characteristics/messageSync');

function GadgetBridgeService() {
  bleno.PrimaryService.call(this, {
    uuid: '13333333-3333-3333-3333-800000000000',
    characteristics: [
      new MessageSyncCharacteristic(),
      new NotificiationCharacteristic(),
    ]
  });
}

util.inherits(GadgetBridgeService, bleno.PrimaryService);

module.exports = GadgetBridgeService;