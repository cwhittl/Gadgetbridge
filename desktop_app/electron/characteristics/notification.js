var util = require('util');
var bleno = require('bleno-mac');

var BlenoCharacteristic = bleno.Characteristic;

var NotificationCharacteristic = function (config) {
  NotificationCharacteristic.super_.call(this, {
    uuid: '13333333-3333-3333-3333-800000000002',
    properties: ['read', 'write', 'notify'],
    value: null
  });
  this._value = new Buffer(0);
  this._updateValueCallback = null;
  this._onSubscribe = typeof config.onSubscribe === "function" ? config.onSubscribe : () => { };
  this._onUnsubscribe = typeof config.onUnsubscribe === "function" ? config.onUnsubscribe : () => { };
  this._onWriteRequest = typeof config.onWriteRequest === "function" ? config.onWriteRequest : () => { };
  this._onReadRequest = typeof config.onReadRequest === "function" ? config.onReadRequest : () => { };
};

util.inherits(NotificationCharacteristic, BlenoCharacteristic);

NotificationCharacteristic.prototype.onReadRequest = function (offset, callback) {
  this._onReadRequest(offset, callback);
  callback(this.RESULT_SUCCESS, this._value);
};

NotificationCharacteristic.prototype.onWriteRequest = function (data, offset, withoutResponse, callback) {
  this._onWriteRequest(data, offset, withoutResponse, callback);
  if (this._updateValueCallback) {
    console.log('NotificationCharacteristic - onWriteRequest: notifying');
    this._updateValueCallback(data);
  }
  callback(this.RESULT_SUCCESS);
};

NotificationCharacteristic.prototype.onSubscribe = function (maxValueSize, updateValueCallback) {
  this._onSubscribe(maxValueSize, updateValueCallback);
  this._updateValueCallback = updateValueCallback;
};

NotificationCharacteristic.prototype.onUnsubscribe = function () {
  this._onUnsubscribe();
  this._updateValueCallback = null;
};


module.exports = NotificationCharacteristic;