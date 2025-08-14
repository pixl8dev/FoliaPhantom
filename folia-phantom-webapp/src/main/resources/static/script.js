document.addEventListener('DOMContentLoaded', () => {
    const uploadBox = document.getElementById('upload-box');
    const fileInput = document.getElementById('file-input');
    const uploadButton = document.getElementById('upload-button');
    const statusBox = document.getElementById('status-box');
    const statusText = document.getElementById('status-text');
    const downloadLink = document.getElementById('download-link');
    const progressBar = document.getElementById('progress-bar');
    const progressBarInner = document.getElementById('progress-bar-inner');

    // Trigger file input when the button is clicked
    uploadButton.addEventListener('click', () => {
        fileInput.click();
    });

    // Trigger file input when the whole box is clicked
    uploadBox.addEventListener('click', (e) => {
        if (e.target.id === 'upload-box' || e.target.tagName === 'P') {
             fileInput.click();
        }
    });

    // Listen for file selection
    fileInput.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (file) {
            handleFileUpload(file);
        }
    });

    // Drag and drop events
    uploadBox.addEventListener('dragover', (e) => {
        e.preventDefault();
        uploadBox.classList.add('dragover');
    });

    uploadBox.addEventListener('dragleave', () => {
        uploadBox.classList.remove('dragover');
    });

    uploadBox.addEventListener('drop', (e) => {
        e.preventDefault();
        uploadBox.classList.remove('dragover');
        const file = e.dataTransfer.files[0];
        if (file && file.name.toLowerCase().endsWith('.jar')) {
            handleFileUpload(file);
        } else {
            showStatus('無効なファイル形式です。.jar ファイルをアップロードしてください。', true);
        }
    });

    function showStatus(message, isError = false) {
        uploadBox.style.display = 'none';
        statusBox.style.display = 'block';
        statusText.textContent = message;
        statusText.className = isError ? 'error' : '';
        downloadLink.style.display = 'none';
        progressBar.style.display = 'none';
    }

    function resetUI() {
        uploadBox.style.display = 'block';
        statusBox.style.display = 'none';
        fileInput.value = ''; // Reset file input
    }

    function handleFileUpload(file) {
        showStatus('アップロード中...');
        progressBar.style.display = 'block';
        progressBarInner.style.width = '0%';

        const formData = new FormData();
        formData.append('file', file);

        const xhr = new XMLHttpRequest();
        xhr.open('POST', '/api/patch', true);

        xhr.upload.onprogress = (event) => {
            if (event.lengthComputable) {
                const percentComplete = (event.loaded / event.total) * 100;
                progressBarInner.style.width = percentComplete + '%';
                if (percentComplete === 100) {
                     showStatus('サーバーでパッチを適用中...');
                }
            }
        };

        xhr.onload = () => {
            progressBar.style.display = 'none';
            if (xhr.status === 200) {
                const blob = xhr.response;
                const url = URL.createObjectURL(blob);
                downloadLink.href = url;
                downloadLink.download = 'patched-' + file.name;
                downloadLink.style.display = 'block';
                showStatus('パッチが正常に完了しました！', false);
                statusText.className = 'success';
            } else {
                showStatus(`エラー: ${xhr.responseText || 'パッチの適用中にエラーが発生しました。'}`, true);
                // Add a button to go back
                const backButton = document.createElement('button');
                backButton.textContent = '戻る';
                backButton.className = 'button';
                backButton.style.marginTop = '1rem';
                backButton.onclick = resetUI;
                statusBox.appendChild(backButton);
            }
        };

        xhr.onerror = () => {
             showStatus('ネットワークエラーが発生しました。', true);
        };

        xhr.responseType = 'blob';
        xhr.send(formData);
    }
});
