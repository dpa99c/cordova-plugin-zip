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
    if (window.cordova && window.resolveLocalFileSystemURL && window.cordova.file) {
        var dataDir = cordova.file.dataDirectory;
        var destZipPath = dataDir + zipFileName;
        // Check if zip file already exists in dataDirectory
        window.resolveLocalFileSystemURL(destZipPath, function() {
            // File exists, proceed to unzip
            var outputDir = dataDir + 'unzip-' + zipFileName.replace('.zip','') + '/';
            doUnzip(destZipPath, outputDir, resultDiv);
        }, function() {
            // File does not exist, copy from assets
            var assetZipPath = null;
            if (cordova.platformId === 'android') {
                assetZipPath = cordova.file.applicationDirectory + 'www/zip/' + zipFileName;
            } else {
                assetZipPath = 'www/zip/' + zipFileName;
            }
            window.resolveLocalFileSystemURL(assetZipPath, function(fileEntry) {
                window.resolveLocalFileSystemURL(dataDir, function(dirEntry) {
                    fileEntry.copyTo(dirEntry, zipFileName, function(newFileEntry) {
                        var outputDir = dataDir + 'unzip-' + zipFileName.replace('.zip','') + '/';
                        doUnzip(newFileEntry.nativeURL, outputDir, resultDiv);
                    }, function(err) {
                        resultDiv.innerText = 'Failed to copy zip file: ' + JSON.stringify(err);
                    });
                }, function(err) {
                    resultDiv.innerText = 'Failed to resolve data directory: ' + JSON.stringify(err);
                });
            }, function(err) {
                resultDiv.innerText = 'Zip file not found in assets: ' + JSON.stringify(err);
            });
        });
    } else {
        // fallback: unzip to same www/zip/ dir (may not work on all platforms)
        var outputDir = 'zip/unzip-' + zipFileName.replace('.zip','') + '/';
        var zipPath = 'zip/' + zipFileName;
        doUnzip(zipPath, outputDir, resultDiv);
    }
}

function doUnzip(zipPath, outputDir, resultDiv) {
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
