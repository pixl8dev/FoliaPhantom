document.addEventListener('DOMContentLoaded', () => {
    const uploadBox = document.getElementById('upload-box');
    const fileInput = document.getElementById('file-input');
    const uploadButton = document.getElementById('upload-button');
    const statusBox = document.getElementById('status-box');
    const statusText = document.getElementById('status-text');
    const progressBar = document.getElementById('progress-bar');
    const progressBarInner = document.getElementById('progress-bar-inner');
    const downloadLink = document.getElementById('download-link');

    // Fetch and apply configuration
    fetch('/api/config')
        .then(response => response.json())
        .then(config => {
            document.getElementById('app-title').textContent = config.title;
            document.getElementById('app-header').textContent = config.header;
            document.getElementById('app-description').textContent = config.description;
        })
        .catch(error => console.error('Error fetching config:', error));

    uploadButton.addEventListener('click', () => {
        fileInput.click();
    });

    fileInput.addEventListener('change', (event) => {
        const file = event.target.files[0];
        if (file) {
            handleFileUpload(file);
        }
    });

    uploadBox.addEventListener('dragover', (event) => {
        event.preventDefault();
        uploadBox.classList.add('dragover');
    });

    uploadBox.addEventListener('dragleave', () => {
        uploadBox.classList.remove('dragover');
    });

    uploadBox.addEventListener('drop', (event) => {
        event.preventDefault();
        uploadBox.classList.remove('dragover');
        const file = event.dataTransfer.files[0];
        if (file) {
            handleFileUpload(file);
        }
    });

    function handleFileUpload(file) {
        uploadBox.style.display = 'none';
        statusBox.style.display = 'block';
        statusText.textContent = `Uploading ${file.name}...`;
        progressBar.style.display = 'block';
        progressBarInner.style.width = '0%';

        const formData = new FormData();
        formData.append('file', file);

        const xhr = new XMLHttpRequest();
        xhr.open('POST', '/api/patch', true);

        xhr.upload.onprogress = (event) => {
            if (event.lengthComputable) {
                const percentComplete = (event.loaded / event.total) * 100;
                progressBarInner.style.width = `${percentComplete}%`;
                statusText.textContent = `Uploading ${file.name}... ${Math.round(percentComplete)}%`;
            }
        };

        xhr.onload = () => {
            if (xhr.status === 200) {
                statusText.textContent = 'Patching complete!';
                progressBar.style.display = 'none';
                downloadLink.style.display = 'block';
                const blob = new Blob([xhr.response], { type: 'application/java-archive' });
                const url = URL.createObjectURL(blob);
                downloadLink.href = url;
                downloadLink.download = `patched-${file.name}`;
            } else {
                statusText.textContent = `Error: ${xhr.statusText}`;
                progressBar.style.display = 'none';
            }
        };

        xhr.onerror = () => {
            statusText.textContent = 'Upload failed.';
            progressBar.style.display = 'none';
        };

        xhr.responseType = 'blob';
        xhr.send(formData);
    }
});
