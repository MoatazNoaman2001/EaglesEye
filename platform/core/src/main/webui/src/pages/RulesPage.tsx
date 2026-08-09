import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

type Rule = {
  id: string;
  name: string;
  type: string;
  severity: string;
  enabled: boolean;
  params: string;
};

export default function RulesPage() {
  const { t } = useTranslation();
  const [rules, setRules] = useState<Rule[]>([]);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<{ ok: boolean; text: string } | null>(null);

  const load = useCallback(() => {
    fetch("/api/v1/alert-rules")
      .then((r) => r.json())
      .then((list: Rule[]) => {
        setRules(list);
        setDrafts(Object.fromEntries(list.map((r) => [r.id, pretty(r.params)])));
      })
      .catch(() => {});
  }, []);

  useEffect(load, [load]);

  const toggle = async (rule: Rule) => {
    await fetch(`/api/v1/alert-rules/${rule.id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ enabled: !rule.enabled }),
    });
    load();
  };

  const saveParams = async (rule: Rule) => {
    let parsed: unknown;
    try {
      parsed = JSON.parse(drafts[rule.id]);
    } catch {
      setMessage({ ok: false, text: `${rule.name}: ${t("rules.badJson")}` });
      return;
    }
    const res = await fetch(`/api/v1/alert-rules/${rule.id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ params: parsed }),
    });
    setMessage({ ok: res.ok, text: `${rule.name}: ${res.ok ? t("rules.saved") : t("rules.error")}` });
    if (res.ok) load();
  };

  return (
    <div className="page">
      <h1 className="title">{t("rules.title")}</h1>
      <p className="subtitle">{t("rules.subtitle")}</p>
      <div className="settings-list">
        {rules.map((rule) => (
          <div className="setting rule" key={rule.id}>
            <div className="info">
              <div className="key">
                <span className={`sev ${rule.severity}`}>{t("severity." + rule.severity, rule.severity)}</span>
                {" "}{rule.name}
              </div>
              <div className="desc">{t("ruleTypes." + rule.type, rule.type)}</div>
              <textarea
                className="params"
                rows={2}
                value={drafts[rule.id] ?? ""}
                onChange={(e) => setDrafts({ ...drafts, [rule.id]: e.target.value })}
              />
            </div>
            <div className="rule-actions">
              <label className="switch">
                <input type="checkbox" checked={rule.enabled} onChange={() => toggle(rule)} />
                <span>{rule.enabled ? t("rules.on") : t("rules.off")}</span>
              </label>
              <button disabled={drafts[rule.id] === pretty(rule.params)} onClick={() => saveParams(rule)}>
                {t("settings.save")}
              </button>
            </div>
          </div>
        ))}
      </div>
      {message && <div className={`msg ${message.ok ? "ok" : "err"}`}>{message.text}</div>}
    </div>
  );
}

function pretty(json: string): string {
  try {
    return JSON.stringify(JSON.parse(json));
  } catch {
    return json;
  }
}
