var util = require('util');
const notifier = require('node-notifier');
var bleno = require('bleno-mac');
const path = require('path');
const iconsDir = './icons';
var Datastore = require('nedb'),
  messageDB = new Datastore({ filename: './data/messages.db', autoload: true });
var app = require('express')();
var http = require('http').Server(app);
var io = require('socket.io')(http);
var port = process.env.PORT || 3000;


var NotificiationCharacteristic = require('./characteristics/notification');
var MessageSyncCharacteristic = require('./characteristics/messageSync');

function GadgetBridgeService() {
  var messageSync = new MessageSyncCharacteristic(messageSyncConfig);
  var notification = new NotificiationCharacteristic(notificationConfig);
  bleno.PrimaryService.call(this, {
    uuid: '13333333-3333-3333-3333-800000000000',
    characteristics: [
      notification,
      messageSync,
    ]
  });
  socketIOInit();
}

let messageUpdateValueCallback = () => {};
let notificationValueCallback = () => { };

var messageSyncConfig = {
  onWriteRequest(data, offset, withoutResponse, callback) {
    console.log('MessageSyncCharacteristic - onWriteRequest: value = ' + data.toString('utf8'));
    const message = JSON.parse(JSON.parse(data.toString('utf8')));
    messageDB.find({ id: message.id }, function (err, docs) {
      if (docs.length === 0) {
        console.log(message.id + ' NOT FOUND');
        messageDB.insert(message);
      }
    });
    io.emit('chat message', message.id);
  },
  onSubscribe(maxValueSize, updateValueCallback) {
    messageUpdateValueCallback = updateValueCallback;
    syncMessages(200);
  }
}

var notificationConfig = {
  onWriteRequest(data, offset, withoutResponse, callback) {
    const dataAsString = data.toString('utf8');
    const dataAsObject = JSON.parse(dataAsString);
    console.log(dataAsObject.title);
    const noficationObj = {
      icon: path.join(iconsDir, 'iconOrig.png'),
    };
    if (({}).hasOwnProperty.call(dataAsObject, 'number')) {
      noficationObj.title = dataAsObject.name;
      noficationObj.message = dataAsObject.number;
    } else {
      noficationObj.title = dataAsObject.title;
      noficationObj.message = dataAsObject.body;
    }
    notifier.notify(noficationObj);
  },
  onSubscribe(maxValueSize, updateValueCallback) {
    notificationUpdateValueCallback = updateValueCallback;
    notifier.notify({
      icon: path.join(iconsDir, 'iconOrig.png'),
      message: 'GadgetBridge Connected',
    });
  },
  onUnsubscribe: () => {
    notifier.notify({
      icon: path.join(iconsDir, 'iconOrig.png'),
      message: 'GadgetBridge Disconnected',
    });
  }
}

function syncMessages(numberOfMessages = 100, updateValueCallback) {
  var obj = { sync: numberOfMessages };
  var data = new Buffer.from(JSON.stringify(obj), "utf-8");
  messageUpdateValueCallback(data);
}

const socketIOInit = function () {
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
}

util.inherits(GadgetBridgeService, bleno.PrimaryService);

module.exports = GadgetBridgeService;