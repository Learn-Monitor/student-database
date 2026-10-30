const queryNext = new URLSearchParams(window.location.search).get("next");
if (queryNext) document.getElementById("next").value = queryNext;

document.getElementById("loginForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const formData = new FormData(event.target);
    const response = await fetch("/login", {
        method: "POST",
        body: new URLSearchParams(formData),
        credentials: "include"
    });
    if (response.ok) {
        // fetch follows the server's redirect. This matters for teacher+admin
        // accounts: the server deliberately redirects them to /select-mode.
        // Do not replace that controlled destination with a client-side
        // unconditional /dashboard redirect.
        const destination = new URL(response.url, window.location.href);
        if (destination.origin !== window.location.origin) {
            window.location.href = "/dashboard";
        } else {
            window.location.href = `${destination.pathname}${destination.search}${destination.hash}`;
        }
    } else {
        alert("Falsches Passwort!");
    }
});
