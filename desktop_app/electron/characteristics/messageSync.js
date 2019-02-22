var util = require('util');
const notifier = require('node-notifier');
var bleno = require('bleno-mac');
const path = require('path');

var BlenoCharacteristic = bleno.Characteristic;

var MessageSyncCharacteristic = function () {
  MessageSyncCharacteristic.super_.call(this, {
    uuid: '13333333-3333-3333-3333-800000000001',
    properties: ['read', 'write', 'notify'],
    value: null
  });

  this._value = new Buffer(0);
  this._updateValueCallback = null;
};

util.inherits(MessageSyncCharacteristic, BlenoCharacteristic);

MessageSyncCharacteristic.prototype.onReadRequest = function (offset, callback) {
  console.log('MessageSyncCharacteristic - onReadRequest: value = ' + this._value.toString('hex'));

  callback(this.RESULT_SUCCESS, this._value);
};

MessageSyncCharacteristic.prototype.onWriteRequest = function (data, offset, withoutResponse, callback) {
  this._value = data;
  console.log('MessageSyncCharacteristic - onWriteRequest: value = ' + this._value.toString('utf8'));

  if (this._updateValueCallback) {
    console.log('MessageSyncCharacteristic - onWriteRequest: notifying');

    this._updateValueCallback(this._value);
  }

  callback(this.RESULT_SUCCESS);
};

MessageSyncCharacteristic.prototype.onSubscribe = function (maxValueSize, updateValueCallback) {
  console.log('MessageSyncCharacteristic - onSubscribe');
  var data = new Buffer.from("608");
  updateValueCallback(data);
  // this._updateValueCallback = updateValueCallback;
};

MessageSyncCharacteristic.prototype.onUnsubscribe = function () {
  console.log('MessageSyncCharacteristic - onUnsubscribe');
  this._updateValueCallback = null;
};

module.exports = MessageSyncCharacteristic;