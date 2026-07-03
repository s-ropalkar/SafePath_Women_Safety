const API = getApiBase();
let googleClientId = "";

if (localStorage.getItem("safepath_token")) {
  location.href = `${getApiBase()}/index.html`;
}

function toast(message, type = "info") {
  const el = document.createElement("div");
  el.className = `toast ${type}`;
  el.textContent = message;
  document.getElementById("toastHost").appendChild(el);
  setTimeout(() => el.remove(), 6000);
}

function saveAuth(user) {
  if (!user || !user.token) {
    toast("Sign-in failed. Please try again.", "danger");
    return;
  }
  localStorage.setItem("safepath_token", user.token);
  localStorage.setItem("safepath_user", JSON.stringify(user));
  location.href = `${getApiBase()}/index.html`;
}

async function apiPost(path, body) {
  let res;
  try {
    res = await fetch(`${API}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch (err) {
    throw new Error("Cannot reach the app server. Run Tasks → safepath: run server.");
  }

  let data;
  try {
    ({ data } = await parseApiJson(res));
  } catch (err) {
    throw new Error(err.message || "Something went wrong. Please try again.");
  }

  if (!res.ok) {
    throw new Error(data.message || "Request failed. Please try again.");
  }
  return data;
}

function setLoading(loading) {
  document.querySelectorAll(".login-card button").forEach((btn) => {
    btn.disabled = loading;
    btn.style.opacity = loading ? "0.6" : "1";
  });
}

function showLoginForm() {
  document.getElementById("loginForm").style.display = "block";
  document.getElementById("signUpForm").style.display = "none";
  document.getElementById("forgotForm").style.display = "none";
}

function showSignUpForm() {
  document.getElementById("loginForm").style.display = "none";
  document.getElementById("signUpForm").style.display = "block";
  document.getElementById("forgotForm").style.display = "none";
}

function showForgotForm() {
  document.getElementById("loginForm").style.display = "none";
  document.getElementById("signUpForm").style.display = "none";
  document.getElementById("forgotForm").style.display = "block";
}

async function forgotPassword() {
  const email = document.getElementById("forgotEmail").value.trim();
  if (!email) {
    toast("Enter your email.", "warn");
    return;
  }
  setLoading(true);
  try {
    const data = await apiPost("/api/forgot-password", { email });
    if (data.emailDelivered) {
      toast("Reset link sent. Check your inbox and spam folder.", "success");
    } else if (data.emailQueued) {
      toast(data.deliveryNote || "Email could not be sent via SMTP. Ask admin to check server logs.", "warn");
    } else {
      toast(data.message || "If that email is registered, reset instructions were sent.", "success");
    }
    showLoginForm();
  } catch (err) {
    toast(err.message, "danger");
  } finally {
    setLoading(false);
  }
}

async function loginUser() {
  const email = document.getElementById("authEmail").value.trim();
  const password = document.getElementById("authPassword").value;
  if (!email || !password) {
    toast("Enter email and password.", "warn");
    return;
  }
  setLoading(true);
  try {
    const data = await apiPost("/api/login", { email, password });
    saveAuth(data.user);
  } catch (err) {
    toast(err.message, "danger");
  } finally {
    setLoading(false);
  }
}

async function signUpUser() {
  const name = document.getElementById("signUpName").value.trim();
  const email = document.getElementById("signUpEmail").value.trim();
  const password = document.getElementById("signUpPassword").value;
  if (!name || !email || !password) {
    toast("Fill in all fields to sign up.", "warn");
    return;
  }
  if (password.length < 6) {
    toast("Password must be at least 6 characters.", "warn");
    return;
  }
  setLoading(true);
  try {
    const data = await apiPost("/api/register", { name, email, password });
    saveAuth(data.user);
  } catch (err) {
    toast(err.message, "danger");
  } finally {
    setLoading(false);
  }
}

async function handleGoogleCredential(response) {
  if (!response?.credential) {
    toast("Google sign-in was cancelled.", "warn");
    setLoading(false);
    return;
  }
  try {
    const data = await apiPost("/api/google-login", { idToken: response.credential });
    saveAuth(data.user);
  } catch (err) {
    toast(err.message, "danger");
  } finally {
    setLoading(false);
  }
}

function waitForGoogleSdk() {
  return new Promise((resolve) => {
    if (window.google?.accounts?.id) {
      resolve(true);
      return;
    }
    const started = Date.now();
    const timer = setInterval(() => {
      if (window.google?.accounts?.id) {
        clearInterval(timer);
        resolve(true);
      } else if (Date.now() - started > 6000) {
        clearInterval(timer);
        resolve(false);
      }
    }, 100);
  });
}

async function loginWithGoogle() {
  if (!googleClientId) {
    toast("Google sign-in is not configured. Use email login or sign up.", "warn");
    return;
  }
  setLoading(true);
  try {
    if (!(await waitForGoogleSdk())) {
      toast("Google sign-in could not load. Check your connection and try again.", "danger");
      return;
    }
    google.accounts.id.initialize({
      client_id: googleClientId,
      callback: handleGoogleCredential,
      auto_select: false,
      cancel_on_tap_outside: true,
      ux_mode: "popup",
    });
    google.accounts.id.prompt((notification) => {
      if (notification.isNotDisplayed() || notification.isSkippedMoment()) {
        google.accounts.id.renderButton(document.getElementById("googleBtnHost"), {
          theme: "filled_black",
          size: "large",
          shape: "pill",
          text: "continue_with",
          width: 320,
        });
        toast("Tap the Google button below to continue.", "info");
        setLoading(false);
      } else if (notification.isDismissedMoment()) {
        setLoading(false);
      }
    });
  } catch (err) {
    toast(err.message, "danger");
    setLoading(false);
  }
}

function mountGoogleButton() {
  if (!googleClientId || !window.google?.accounts?.id) return;
  const host = document.getElementById("googleBtnHost");
  if (!host || host.childElementCount > 0) return;
  google.accounts.id.initialize({
    client_id: googleClientId,
    callback: handleGoogleCredential,
  });
  google.accounts.id.renderButton(host, {
    theme: "filled_black",
    size: "large",
    shape: "pill",
    text: "continue_with",
    width: 320,
  });
}

async function initLoginPage() {
  try {
    const cfg = await fetch(`${API}/api/config`).then((r) => r.json());
    googleClientId = cfg.googleClientId || "";
    if (cfg.port) sessionStorage.setItem("safepath_port", String(cfg.port));
    if (!cfg.googleConfigured) {
      document.getElementById("googleLoginBtn").style.display = "none";
      document.querySelector(".login-divider").style.display = "none";
    } else {
      waitForGoogleSdk().then(() => mountGoogleButton());
    }
  } catch (_) {
    /* server offline — origin.js shows start instructions */
  }

  document.getElementById("googleLoginBtn").addEventListener("click", loginWithGoogle);
  document.getElementById("loginBtn").addEventListener("click", loginUser);
  document.getElementById("signUpBtn").addEventListener("click", signUpUser);
  document.getElementById("forgotBtn")?.addEventListener("click", forgotPassword);
  document.getElementById("showSignUpLink").addEventListener("click", (e) => {
    e.preventDefault();
    showSignUpForm();
  });
  document.getElementById("showLoginLink").addEventListener("click", (e) => {
    e.preventDefault();
    showLoginForm();
  });
  document.getElementById("forgotPasswordLink")?.addEventListener("click", (e) => {
    e.preventDefault();
    showForgotForm();
  });
  document.getElementById("backToLoginLink")?.addEventListener("click", (e) => {
    e.preventDefault();
    showLoginForm();
  });

  document.getElementById("authPassword").addEventListener("keydown", (e) => {
    if (e.key === "Enter") loginUser();
  });
  document.getElementById("signUpPassword").addEventListener("keydown", (e) => {
    if (e.key === "Enter") signUpUser();
  });
}

document.addEventListener("DOMContentLoaded", initLoginPage);
