var util = require('util');

var bleno = require('bleno-mac');

var NotificiationCharacteristic = require('./characteristics/notification');
var MessageSyncCharacteristic = require('./characteristics/messageSync');

function GadgetBridgeService(config) {
  bleno.PrimaryService.call(this, {
    uuid: '13333333-3333-3333-3333-800000000000',
    characteristics: [
      new NotificiationCharacteristic(config),
      new MessageSyncCharacteristic(config),
    ]
  });
}
// setTimeout(() => {
//   var data = new Buffer.from("608");
//   messageSync.updateValue(data);
// }, 10000)


util.inherits(GadgetBridgeService, bleno.PrimaryService);

module.exports = GadgetBridgeService;