import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

type Setting = {
  key: string;
  value: string;
  valueType: string;
  description: string;
};

export default function SettingsPage() {
  const { t } = useTranslation();
  const [settings, setSettings] = useState<Setting[]>([]);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    fetch("/api/v1/admin/settings")
      .then((r) => r.json())
      .then((list: Setting[]) => {
        setSettings(list);
        setDrafts(Object.fromEntries(list.map((s) => [s.key, s.value])));
      })
      .catch(() => {});
  }, []);

  const save = async (key: string) => {
    try {
      const res = await fetch(`/api/v1/admin/settings/${key}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ value: drafts[key] }),
      });
      if (!res.ok) throw new Error(String(res.status));
      const updated: Setting = await res.json();
      setSettings((prev) => prev.map((s) => (s.key === key ? updated : s)));
      setMessage({ ok: true, text: `${key}: ${t("settings.saved")}` });
    } catch {
      setMessage({ ok: false, text: `${key}: ${t("settings.error")}` });
    }
  };

  return (
    <div className="page">
      <h1 className="title">{t("settings.title")}</h1>
      <p className="subtitle">{t("settings.subtitle")}</p>
      <div className="settings-list">
        {settings.map((s) => (
          <div className="setting" key={s.key}>
            <div className="info">
              <div className="key">{s.key}</div>
              <div className="desc">{s.description}</div>
            </div>
            <input
              value={drafts[s.key] ?? ""}
              onChange={(e) => setDrafts({ ...drafts, [s.key]: e.target.value })}
            />
            <button disabled={drafts[s.key] === s.value} onClick={() => save(s.key)}>
              {t("settings.save")}
            </button>
          </div>
        ))}
      </div>
      {message && <div className={`msg ${message.ok ? "ok" : "err"}`}>{message.text}</div>}
    </div>
  );
}
