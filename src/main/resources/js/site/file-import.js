document.getElementById('importFile').addEventListener('submit', async (event) => {
    event.preventDefault();
    const fileInput = document.getElementById('lptFile');
    if (!fileInput.files.length) {
        alert("Bitte wählen Sie eine Datei aus.");
        return;
    }
    const response = await fetch(event.currentTarget.dataset.uploadUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'text/plain' },
        body: await fileInput.files[0].text()
    });
    alert(response.ok ? "Datei erfolgreich hochgeladen!" : "Fehler beim Hochladen der Datei.");
});
