$(document).ready(function () {

    let term = new Terminal({
        fontFamily: "'Ubuntu Mono', monospace",
        theme: { background: '#1e1e1e', foreground: '#ffffff' },
        cursorBlink: true
    });

    term.open(document.getElementById('terminal-container'));

    $('#connect-btn').click(function () {
        const server = $('#server').val();
        const port = $('#port').val() || 22;
        if (!server) return;
        $('#connect-form').hide();
        $('#terminal-container').show();
        term.write('Connecting to ' + server + ':' + port + '...\r\n');
        javaBridge.setSSHInfo(server, parseInt(port));
    });

    term.onData(function(data) {
        javaBridge.send(data);
    });

});
