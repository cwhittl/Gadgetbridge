var util = require('util');
var Datastore = require('nedb'), 
  messageDB = new Datastore({ filename: '../data/messages.db', autoload: true });

var bleno = require('bleno-mac');

var BlenoCharacteristic = bleno.Characteristic;

var MessageSyncCharacteristic = function () {
  MessageSyncCharacteristic.super_.call(this, {
    uuid: '13333333-3333-3333-3333-800000000001',
    properties: ['read', 'write', 'notify'],
    value: null,
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
  const message = JSON.parse(JSON.parse(data.toString('utf8')));
  messageDB.find({ id: message.id }, function (err, docs) {
    if(docs.length === 0) {
      console.log(message.id + ' NOT FOUND');
      messageDB.insert(message);
    }
  });
  // if (this._updateValueCallback) {
  //   console.log('MessageSyncCharacteristic - onWriteRequest: notifying');

  //   this._updateValueCallback(this._value);
  // }

  callback(this.RESULT_SUCCESS);
};

MessageSyncCharacteristic.prototype.onSubscribe = function (maxValueSize, updateValueCallback) {
  console.log('MessageSyncCharacteristic - onSubscribe - ' + maxValueSize);
  this._updateValueCallback = updateValueCallback;
  messageDB
    .find({})
    .sort({ createdAt: -1 })      // OR `.sort({ updatedAt: -1 })` to sort by last modification time
    .limit(1)
    .exec(function (err, docs) {
      var obj = { sync: docs[0].id };
      var data = new Buffer.from(JSON.stringify(obj), "utf-8");
      updateValueCallback(data);
    });
  
};

MessageSyncCharacteristic.prototype.onUnsubscribe = function () {
  console.log('MessageSyncCharacteristic - onUnsubscribe');
  this._updateValueCallback = null;
};

module.exports = MessageSyncCharacteristic;