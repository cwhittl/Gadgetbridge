var menubar = require('menubar')
var bleno = require('bleno-mac');
var GadgetBridgeService = require('./service');
const { dialog } = require('electron')
var app = require('express')();
var http = require('http').Server(app);
const { protocol } = require('electron')
var io = require('socket.io')(http);
var port = process.env.PORT || 3000;

const config = {
  iconsDir: __dirname + '/icons/',
  io,
}
var gadgetBridgeService = new GadgetBridgeService(config);

app.get('/', function (req, res) {
  res.sendFile(__dirname + '/index.html');
});

io.on('connection', function (socket) {
  socket.on('chat message', function (msg) {
    io.emit('chat message', msg);
  });
});

http.listen(port, function () {
  console.log('listening on *:' + port);
});

var mb = menubar({ icon: './icons/IconTemplate.png', width: 800, height: 600 })
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
