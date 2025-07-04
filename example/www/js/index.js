
document.addEventListener('deviceready', onDeviceReady, false);

function onDeviceReady() {

    console.log('Running cordova-' + cordova.platformId + '@' + cordova.version);
    setupUnzipTests();
}

function setupUnzipTests() {
    var validBtn = document.getElementById('unzip-valid');
    var invalidBtn = document.getElementById('unzip-invalid');
    var resultDiv = document.getElementById('result');

    if (!window.zip) {
        resultDiv.innerText = 'cordova-plugin-zip not available.';
        return;
    }

    validBtn.addEventListener('click', function() {
        runUnzipTest('valid.zip');
    });
    invalidBtn.addEventListener('click', function() {
        runUnzipTest('invalid.zip');
    });
}

function runUnzipTest(zipFileName) {
    var resultDiv = document.getElementById('result');
    resultDiv.innerHTML = 'Unzipping <b>' + zipFileName + '</b>...';

    // Use cordova-plugin-file to get the output directory
    var outputDir = null;
    if (window.cordova && window.resolveLocalFileSystemURL) {
        window.resolveLocalFileSystemURL(cordova.file.dataDirectory, function(dirEntry) {
            outputDir = dirEntry.toURL() + 'unzip-' + zipFileName.replace('.zip','') + '/';
            doUnzip(zipFileName, outputDir, resultDiv);
        }, function(err) {
            resultDiv.innerText = 'Failed to resolve output directory: ' + JSON.stringify(err);
        });
    } else {
        // fallback: unzip to same www/zip/ dir (may not work on all platforms)
        outputDir = 'zip/unzip-' + zipFileName.replace('.zip','') + '/';
        doUnzip(zipFileName, outputDir, resultDiv);
    }
}

function doUnzip(zipFileName, outputDir, resultDiv) {
    var zipPath = 'zip/' + zipFileName;
    if (window.cordova && window.cordova.file && window.cordova.file.applicationDirectory) {
        // Use absolute path for applicationDirectory
        zipPath = cordova.file.applicationDirectory + 'www/zip/' + zipFileName;
    }
    if (window.zip && typeof window.zip.unzip === 'function') {
        window.zip.unzip(zipPath, outputDir, function(result) {
            if (typeof result === 'object' && result.code) {
                resultDiv.innerHTML = '<span style="color:red">Unzip failed: [' + result.code + '] ' + result.message + '</span>';
            } else if (result === 0) {
                resultDiv.innerHTML = '<span style="color:green">Unzip succeeded!</span><br>Output: ' + outputDir;
            } else {
                resultDiv.innerHTML = '<span style="color:red">Unzip failed: Unknown error</span>';
            }
        }, function(progress) {
            if (progress && typeof progress.loaded !== 'undefined' && typeof progress.total !== 'undefined') {
                resultDiv.innerHTML = 'Progress: ' + progress.loaded + ' / ' + progress.total;
            }
        });
    } else {
        resultDiv.innerText = 'cordova-plugin-zip not available.';
    }
}
