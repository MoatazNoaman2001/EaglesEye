import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

type Digest = {
  id: string;
  digestDate: string;
  textAr: string;
  textEn: string;
  stats: string;
};

export default function DigestPage() {
  const { t, i18n } = useTranslation();
  const [digests, setDigests] = useState<Digest[]>([]);
  const [selected, setSelected] = useState<Digest | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    fetch("/api/v1/digests")
      .then((r) => r.json())
      .then((list: Digest[]) => {
        setDigests(list);
        setSelected((cur) => cur ?? list[0] ?? null);
      })
      .catch(() => {});
  }, []);

  useEffect(load, [load]);

  const generate = async () => {
    setBusy(true);
    const today = new Date().toISOString().slice(0, 10);
    await fetch(`/api/v1/digests/generate?date=${today}`, { method: "POST" }).catch(() => {});
    setBusy(false);
    setSelected(null);
    load();
  };

  const text = selected ? (i18n.language === "ar" ? selected.textAr : selected.textEn) : "";

  return (
    <div className="page">
      <h1 className="title">{t("digest.title")}</h1>
      <p className="subtitle">{t("digest.subtitle")}</p>
      <div className="digest-layout">
        <div className="digest-list">
          <button className="btn-small generate" onClick={generate} disabled={busy}>
            {busy ? "…" : t("digest.generateToday")}
          </button>
          {digests.map((d) => (
            <button
              key={d.id}
              className={`digest-day ${selected?.id === d.id ? "active" : ""}`}
              onClick={() => setSelected(d)}
            >
              {d.digestDate}
            </button>
          ))}
          {digests.length === 0 && <p className="subtitle">{t("digest.none")}</p>}
        </div>
        {selected && (
          <div className="digest-bubble" dir={i18n.language === "ar" ? "rtl" : "ltr"}>
            <pre>{text}</pre>
            <div className="bubble-foot">{t("digest.channelNote")}</div>
          </div>
        )}
      </div>
    </div>
  );
}
