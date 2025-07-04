var exec = cordova.require('cordova/exec');

function newProgressEvent(result) {
    var event = {
            loaded: result.loaded,
            total: result.total
    };
    return event;
}

exports.unzip = function(fileName, outputDirectory, callback, progressCallback) {
    var win = function(result) {
        if (result && typeof result.loaded != "undefined") {
            if (progressCallback) {
                return progressCallback(newProgressEvent(result));
            }
        } else if (callback) {
            callback(0);
        }
    };
    var fail = function(result) {
        if (callback) {
            // If result is an error object with code/message, pass it through
            if (result && typeof result === 'object' && result.code && result.message) {
                callback(result);
            } else {
                callback({ code: 'UNKNOWN_ERROR', message: result || 'Unzip failed' });
            }
        }
    };
    exec(win, fail, 'Zip', 'unzip', [fileName, outputDirectory]);
};
