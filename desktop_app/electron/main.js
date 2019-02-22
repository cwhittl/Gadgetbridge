var menubar = require('menubar')
var bleno = require('bleno-mac');
var GadgetBridgeService = require('./service');
var gadgetBridgeService = new GadgetBridgeService();
var mb = menubar({ icon: './icons/IconTemplate.png', width: 150, height: 60 })
mb.on('ready', function ready () {
  // console.log('app is ready')
  bleno.on('stateChange', function (state) {
    // console.log('on -> stateChange: ' + state);

    if (state === 'poweredOn') {
      bleno.startAdvertising('GadgetBridgeDesktop', [gadgetBridgeService.uuid]);
    } else {
      bleno.stopAdvertising();
    }
  });

  bleno.on('advertisingStart', function (error) {
    // console.log('on -> advertisingStart: ' + (error ? 'error ' + error : 'success'));

    if (!error) {
      bleno.setServices([gadgetBridgeService], function (error) {
        console.log('setServices: ' + (error ? 'error ' + error : 'success'));
      });
    }
  });
})
