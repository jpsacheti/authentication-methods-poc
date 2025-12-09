// --- Base64URL Utilities ---
function bufferToBase64url(buffer) {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary)
        .replace(/\+/g, '-')
        .replace(/\//g, '_')
        .replace(/=/g, '');
}

function base64urlToBuffer(base64url) {
    const padding = '='.repeat((4 - base64url.length % 4) % 4);
    const base64 = (base64url + padding)
        .replace(/-/g, '+')
        .replace(/_/g, '/');
    const rawData = atob(base64);
    const outputArray = new Uint8Array(rawData.length);
    for (let i = 0; i < rawData.length; ++i) {
        outputArray[i] = rawData.charCodeAt(i);
    }
    return outputArray.buffer;
}

// --- WebAuthn Logic ---

async function registerWith(attachment) {
    const username = document.getElementById('username').value;
    if (!username) return showMessage('Please enter a username', 'red');

    try {
        showMessage('Starting registration...', 'blue');

        // 1. Get options from server
        const startResponse = await fetch(`/webauthn/register/start?username=${encodeURIComponent(username)}&attachment=${encodeURIComponent(attachment || 'platform')}`, { method: 'POST' });
        if (!startResponse.ok) throw new Error('Failed to start registration');
        const options = await startResponse.json();

        // 2. Decode options
        options.publicKey.challenge = base64urlToBuffer(options.publicKey.challenge);
        options.publicKey.user.id = base64urlToBuffer(options.publicKey.user.id);
        if (options.publicKey.excludeCredentials) {
            options.publicKey.excludeCredentials.forEach(c => {
                c.id = base64urlToBuffer(c.id);
            });
        }

        // 3. Create credential
        const credential = await navigator.credentials.create(options);

        // 4. Encode response
        const credentialJson = JSON.stringify({
            id: credential.id,
            rawId: bufferToBase64url(credential.rawId),
            type: credential.type,
            response: {
                attestationObject: bufferToBase64url(credential.response.attestationObject),
                clientDataJSON: bufferToBase64url(credential.response.clientDataJSON)
            }
        });

        // 5. Finish registration
        const finishResponse = await fetch(`/webauthn/register/finish?username=${encodeURIComponent(username)}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: credentialJson
        });

        if (finishResponse.ok) {
            showMessage('Registration successful!', 'green');
        } else {
            throw new Error('Server rejected registration');
        }

    } catch (err) {
        console.error(err);
        showMessage('Registration failed: ' + err.message, 'red');
    }
}

async function login() {
    const username = document.getElementById('username').value;
    if (!username) return showMessage('Please enter a username', 'red');

    try {
        showMessage('Starting login...', 'blue');

        // 1. Get options from server
        const startResponse = await fetch(`/webauthn/login/start?username=${username}`, { method: 'POST' });
        if (!startResponse.ok) throw new Error('Failed to start login');
        const options = await startResponse.json();

        // 2. Decode options
        options.publicKey.challenge = base64urlToBuffer(options.publicKey.challenge);
        if (options.publicKey.allowCredentials) {
            options.publicKey.allowCredentials.forEach(c => {
                c.id = base64urlToBuffer(c.id);
            });
        }

        // 3. Get assertion
        const assertion = await navigator.credentials.get(options);

        // 4. Encode response
        const assertionJson = JSON.stringify({
            id: assertion.id,
            rawId: bufferToBase64url(assertion.rawId),
            type: assertion.type,
            response: {
                authenticatorData: bufferToBase64url(assertion.response.authenticatorData),
                clientDataJSON: bufferToBase64url(assertion.response.clientDataJSON),
                signature: bufferToBase64url(assertion.response.signature),
                userHandle: assertion.response.userHandle ? bufferToBase64url(assertion.response.userHandle) : null
            }
        });

        // 5. Finish login
        const finishResponse = await fetch(`/webauthn/login/finish?username=${username}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: assertionJson
        });

        if (finishResponse.ok) {
            const data = await finishResponse.json();
            showMessage('Login successful! Token: ' + data.token.substring(0, 20) + '...', 'green');
        } else {
            throw new Error('Server rejected login');
        }

    } catch (err) {
        console.error(err);
        showMessage('Login failed: ' + err.message, 'red');
    }
}

function showMessage(text, color) {
    const el = document.getElementById('message');
    el.innerText = text;
    el.style.color = color || 'black';
}
