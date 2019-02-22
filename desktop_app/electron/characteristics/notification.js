var util = require('util');
const notifier = require('node-notifier');
var bleno = require('bleno-mac');
const path = require('path');

var BlenoCharacteristic = bleno.Characteristic;

var NotificationCharacteristic = function () {
  NotificationCharacteristic.super_.call(this, {
    uuid: '13333333-3333-3333-3333-800000000002',
    properties: ['read', 'write', 'notify'],
    value: null
  });

  this._value = new Buffer(0);
  this._updateValueCallback = null;
};

util.inherits(NotificationCharacteristic, BlenoCharacteristic);

NotificationCharacteristic.prototype.onReadRequest = function (offset, callback) {
  console.log('NotificationCharacteristic - onReadRequest: value = ' + this._value.toString('hex'));

  callback(this.RESULT_SUCCESS, this._value);
};

NotificationCharacteristic.prototype.onWriteRequest = function (data, offset, withoutResponse, callback) {
  this._value = data;
  console.log('NotificationCharacteristic - onWriteRequest: value = ' + this._value.toString('utf8'));

  const dataAsString = this._value.toString('utf8');
  const dataAsObject = JSON.parse(dataAsString);
  console.log(dataAsObject.title);
  const noficationObj = {
    icon: path.join(__dirname, 'icons/iconOrig.png'),
  };
  if (({}).hasOwnProperty.call(dataAsObject, 'number')) {
    noficationObj.title = dataAsObject.name;
    noficationObj.message = dataAsObject.number;
  } else {
    noficationObj.title = dataAsObject.title;
    noficationObj.message = dataAsObject.body;
  }
  notifier.notify(noficationObj);

  if (this._updateValueCallback) {
    console.log('NotificationCharacteristic - onWriteRequest: notifying');

    this._updateValueCallback(this._value);
  }

  callback(this.RESULT_SUCCESS);
};

NotificationCharacteristic.prototype.onSubscribe = function (maxValueSize, updateValueCallback) {
  console.log('NotificationCharacteristic - onSubscribe');
  notifier.notify({
    icon: path.join(__dirname, 'icons/iconOrig.png'),
    message: 'GadgetBridge Connected',
  });
  this._updateValueCallback = updateValueCallback;
};

NotificationCharacteristic.prototype.onUnsubscribe = function () {
  console.log('NotificationCharacteristic - onUnsubscribe');
  notifier.notify({
    icon: path.join(__dirname, 'icons/iconOrig.png'),
    message: 'GadgetBridge Disconnected',
  });
  this._updateValueCallback = null;
};

module.exports = NotificationCharacteristic;