var util = require('util');

var bleno = require('bleno-mac');
const path = require('path');
const objectByteConverter = require('object-byte-converter');

var BlenoCharacteristic = bleno.Characteristic;

var MessageSyncCharacteristic = function () {
  MessageSyncCharacteristic.super_.call(this, {
    uuid: '13333333-3333-3333-3333-800000000001',
    properties: ['read', 'write', 'notify'],
    value: null,
    maxValueSize: 2000,
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

  // if (this._updateValueCallback) {
  //   console.log('MessageSyncCharacteristic - onWriteRequest: notifying');

  //   this._updateValueCallback(this._value);
  // }

  callback(this.RESULT_SUCCESS);
};

MessageSyncCharacteristic.prototype.onSubscribe = function (maxValueSize, updateValueCallback) {
  console.log('MessageSyncCharacteristic - onSubscribe - ' + maxValueSize);
  this._updateValueCallback = updateValueCallback;
  var obj = { sync1: 608 };
  var data = new Buffer.from(JSON.stringify(obj), "utf-8");
  // const example_typedefs = {
  //   _settings_: {
  //     // CPU endianess (Little endian if not specified)
  //     isBigEndian: true
  //   },
  //   // Typedef for struct1
  //   struct1: {
  //     // int field1;
  //     lastId: {
  //       type: "int", // Signed integer e.g. char, short, int, long
  //       size: 10 // Number of byte
  //     },
  //     // char field3[30]
  //     command: {
  //       type: "string", // String e.g. char[30]
  //       size: 30 // Size of char array
  //     },
  //   },
  // };
  // const mainbytesArray = objectByteConverter.toByteArray(obj, "struct1", example_typedefs);
  // var data = new Buffer.from(mainbytesArray, "utf-8");
  // var temp = JSON.parse(data.toString());
  console.log(data);
  updateValueCallback(data);
};

MessageSyncCharacteristic.prototype.onUnsubscribe = function () {
  console.log('MessageSyncCharacteristic - onUnsubscribe');
  this._updateValueCallback = null;
};

module.exports = MessageSyncCharacteristic;