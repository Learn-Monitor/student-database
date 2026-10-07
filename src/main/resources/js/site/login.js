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
        const next = document.getElementById("next").value;
        window.location.href = next && next.startsWith("/") && !next.startsWith("//") ? next : "/dashboard";
    } else {
        alert("Falsches Passwort!");
    }
});
