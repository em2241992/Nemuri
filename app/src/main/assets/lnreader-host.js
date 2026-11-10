(function () {
  "use strict";

  const loadedPlugins = Object.create(null);

  function createCheerioNode(el, rootDoc) {
    const elements = Array.isArray(el) ? el : el ? [el] : [];
    const api = {
      length: elements.length,
      get: function (idx) {
        return idx === undefined ? elements : elements[idx];
      },
      first: function () {
        return createCheerioNode(elements[0] ? [elements[0]] : [], rootDoc);
      },
      last: function () {
        return createCheerioNode(
          elements.length > 0 ? [elements[elements.length - 1]] : [],
          rootDoc
        );
      },
      eq: function (idx) {
        const target = elements[idx < 0 ? elements.length + idx : idx];
        return createCheerioNode(target ? [target] : [], rootDoc);
      },
      find: function (selector) {
        const out = [];
        for (let i = 0; i < elements.length; i++) {
          const node = elements[i];
          if (node && node.querySelectorAll) {
            const found = node.querySelectorAll(selector);
            for (let j = 0; j < found.length; j++) out.push(found[j]);
          }
        }
        return createCheerioNode(out, rootDoc);
      },
      children: function (selector) {
        const out = [];
        for (let i = 0; i < elements.length; i++) {
          const kids = elements[i] ? elements[i].children || [] : [];
          for (let j = 0; j < kids.length; j++) {
            if (!selector || (kids[j].matches && kids[j].matches(selector))) {
              out.push(kids[j]);
            }
          }
        }
        return createCheerioNode(out, rootDoc);
      },
      parent: function () {
        const out = [];
        for (let i = 0; i < elements.length; i++) {
          if (elements[i] && elements[i].parentElement) {
            out.push(elements[i].parentElement);
          }
        }
        return createCheerioNode(out, rootDoc);
      },
      next: function () {
        const out = [];
        for (let i = 0; i < elements.length; i++) {
          if (elements[i] && elements[i].nextElementSibling) {
            out.push(elements[i].nextElementSibling);
          }
        }
        return createCheerioNode(out, rootDoc);
      },
      prev: function () {
        const out = [];
        for (let i = 0; i < elements.length; i++) {
          if (elements[i] && elements[i].previousElementSibling) {
            out.push(elements[i].previousElementSibling);
          }
        }
        return createCheerioNode(out, rootDoc);
      },
      text: function () {
        return elements.map((n) => (n && n.textContent ? n.textContent : "")).join("").trim();
      },
      html: function () {
        return elements[0] ? elements[0].innerHTML || "" : "";
      },
      attr: function (name) {
        if (!elements[0] || !elements[0].getAttribute) return undefined;
        const val = elements[0].getAttribute(name);
        return val === null ? undefined : val;
      },
      remove: function () {
        for (let i = 0; i < elements.length; i++) {
          if (elements[i] && elements[i].remove) elements[i].remove();
        }
        return api;
      },
      each: function (fn) {
        for (let i = 0; i < elements.length; i++) {
          fn.call(elements[i], i, elements[i]);
        }
        return api;
      },
      map: function (fn) {
        const mapped = [];
        for (let i = 0; i < elements.length; i++) {
          const res = fn.call(elements[i], i, elements[i]);
          if (res !== undefined && res !== null) mapped.push(res);
        }
        return {
          get: function () {
            return mapped;
          },
          toArray: function () {
            return mapped;
          },
        };
      },
      toArray: function () {
        return elements.slice();
      },
    };
    return api;
  }

  function loadHtml(htmlString) {
    const parser = new DOMParser();
    const doc = parser.parseFromString(String(htmlString || ""), "text/html");
    const $ = function (selector, context) {
      if (!selector) return createCheerioNode([], doc);
      if (typeof selector === "string") {
        const trimmed = selector.trim();
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
          const frag = parser.parseFromString(trimmed, "text/html");
          return createCheerioNode(Array.from(frag.body.children), doc);
        }
        const root = context && context.querySelectorAll ? context : doc;
        try {
          return createCheerioNode(Array.from(root.querySelectorAll(selector)), doc);
        } catch (_e) {
          return createCheerioNode([], doc);
        }
      }
      if (selector.nodeType) {
        return createCheerioNode([selector], doc);
      }
      if (Array.isArray(selector)) {
        return createCheerioNode(selector, doc);
      }
      return selector;
    };
    $.html = function (node) {
      if (!node) return doc.documentElement ? doc.documentElement.outerHTML : "";
      if (node.html) return node.html();
      return node.outerHTML || "";
    };
    return $;
  }

  async function nativeFetch(url, init) {
    const method = (init && init.method) || "GET";
    const headersObj = (init && init.headers) || {};
    const bodyStr = init && init.body ? String(init.body) : null;
    if (window.AndroidLnBridge && window.AndroidLnBridge.httpRequest) {
      const rawResp = window.AndroidLnBridge.httpRequest(
        String(url),
        String(method),
        JSON.stringify(headersObj),
        bodyStr
      );
      const parsed = JSON.parse(rawResp);
      if (parsed.error) {
        throw new Error(parsed.error);
      }
      return {
        ok: parsed.status >= 200 && parsed.status < 300,
        status: parsed.status,
        url: parsed.url || String(url),
        text: async function () {
          return parsed.body || "";
        },
        json: async function () {
          return JSON.parse(parsed.body || "{}");
        },
      };
    }
    return fetch(url, init);
  }

  async function fetchText(url, init) {
    const r = await nativeFetch(url, init);
    return await r.text();
  }

  const libsRegistry = {
    "@libs/fetch": {
      fetchApi: nativeFetch,
      fetchText: fetchText,
      fetchFile: fetchText,
    },
    "@libs/novelStatus": {
      NovelStatus: {
        Unknown: "Unknown",
        Ongoing: "Ongoing",
        Completed: "Completed",
        Licensed: "Licensed",
        PublishingFinished: "Publishing Finished",
        Cancelled: "Cancelled",
        OnHiatus: "On Hiatus",
      },
    },
    "@libs/filterInputs": {
      FilterTypes: {
        TextInput: "Text",
        Picker: "Picker",
        CheckboxGroup: "Checkbox",
        Switch: "Switch",
        ExcludableCheckboxGroup: "XCheckbox",
      },
    },
    "@libs/defaultCover": {
      defaultCover: "",
    },
    "@libs/isAbsoluteUrl": {
      isUrlAbsolute: function (u) {
        return /^https?:\/\//i.test(String(u || ""));
      },
    },
    "@libs/storage": {
      storage: {
        get: function () { return null; },
        set: function () {},
      },
      localStorage: {
        get: function () { return null; },
        set: function () {},
      },
      sessionStorage: {
        get: function () { return null; },
        set: function () {},
      },
    },
    cheerio: {
      load: loadHtml,
      default: { load: loadHtml },
    },
    dayjs: function (d) {
      const dt = d ? new Date(d) : new Date();
      return {
        toISOString: function () {
          return isNaN(dt.getTime()) ? new Date().toISOString() : dt.toISOString();
        },
        valueOf: function () {
          return isNaN(dt.getTime()) ? Date.now() : dt.getTime();
        },
        format: function () {
          return isNaN(dt.getTime()) ? "" : dt.toISOString();
        },
      };
    },
  };

  function customRequire(modName) {
    if (libsRegistry[modName]) return libsRegistry[modName];
    return {};
  }

  window.__lnHost = {
    loadPlugin: function (pluginId, jsCode) {
      const module = { exports: {} };
      const exports = module.exports;
      const fn = new Function("require", "module", "exports", jsCode);
      fn(customRequire, module, exports);
      const resolved =
        module.exports && module.exports.default
          ? module.exports.default
          : module.exports;
      loadedPlugins[pluginId] = resolved;
      return JSON.stringify({
        id: resolved.id || pluginId,
        name: resolved.name || pluginId,
        site: resolved.site || "",
        version: resolved.version || "1.0.0",
        icon: resolved.icon || "",
      });
    },
    callPlugin: function (callId, pluginId, methodName, argsJson) {
      (async function () {
        try {
          const plugin = loadedPlugins[pluginId];
          if (!plugin) {
            throw new Error("Plugin not loaded: " + pluginId);
          }
          const fn = plugin[methodName];
          if (typeof fn !== "function") {
            throw new Error("Method not found on plugin " + pluginId + ": " + methodName);
          }
          const args = JSON.parse(argsJson || "[]");
          const result = await fn.apply(plugin, args);
          window.AndroidLnBridge.onJsResult(callId, JSON.stringify(result ?? null), null);
        } catch (err) {
          window.AndroidLnBridge.onJsResult(
            callId,
            null,
            err && err.message ? String(err.message) : String(err)
          );
        }
      })();
    },
  };
})();
