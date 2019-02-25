var menubar = require('menubar')
var bleno = require('bleno-mac');
var GadgetBridgeService = require('./service');

var gadgetBridgeService = new GadgetBridgeService();

var mb = menubar({ icon: './icons/IconTemplate.png', width: 800, height: 600 })
mb.on('ready', function ready () {
  initWindow();
  blenoInit();
});

const initWindow = function() {
  //Needed to connect to socket.io to html page
  mb.showWindow();
  setTimeout(() => {
    mb.hideWindow();
  }, 300);
}

const blenoInit = function() {
  bleno.on('stateChange', function (state) {
    if (state === 'poweredOn') {
      bleno.startAdvertising('GadgetBridgeDesktop', [gadgetBridgeService.uuid]);
    } else {
      bleno.stopAdvertising();
    }
  });

  bleno.on('advertisingStart', function (error) {
    if (!error) {
      bleno.setServices([gadgetBridgeService], function (error) {
        console.log('setServices: ' + (error ? 'error ' + error : 'success'));
      });
    }
  });
}

