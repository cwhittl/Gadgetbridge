var util = require('util');
var bleno = require('bleno-mac');

var BlenoCharacteristic = bleno.Characteristic;

var MessageSyncCharacteristic = function (config) {
  MessageSyncCharacteristic.super_.call(this, {
    uuid: '13333333-3333-3333-3333-800000000001',
    properties: ['read', 'write', 'notify'],
    value: null,
  });
  this._value = new Buffer(0);
  this._updateValueCallback = null;
  this._onSubscribe = typeof config.onSubscribe === "function" ? config.onSubscribe : () => { };
  this._onUnsubscribe = typeof config.onUnsubscribe === "function" ? config.onUnsubscribe : () => { };
  this._onWriteRequest = typeof config.onWriteRequest === "function" ? config.onWriteRequest : () => { };
  this._onReadRequest = typeof config.onReadRequest === "function" ? config.onReadRequest : () => {};
};

util.inherits(MessageSyncCharacteristic, BlenoCharacteristic);

MessageSyncCharacteristic.prototype.onReadRequest = function (offset, callback) {
  this._onReadRequest(offset, callback);
  callback(this.RESULT_SUCCESS, this._value);
};

MessageSyncCharacteristic.prototype.onWriteRequest = function (data, offset, withoutResponse, callback) {
  this._onWriteRequest(data, offset, withoutResponse, callback);
  callback(this.RESULT_SUCCESS);
};

MessageSyncCharacteristic.prototype.onSubscribe = function (maxValueSize, updateValueCallback) {
  this._onSubscribe(maxValueSize, updateValueCallback);
  this._updateValueCallback = updateValueCallback;
};

MessageSyncCharacteristic.prototype.onUnsubscribe = function () {
  this._onUnsubscribe();
  this._updateValueCallback = null;
};

module.exports = MessageSyncCharacteristic;