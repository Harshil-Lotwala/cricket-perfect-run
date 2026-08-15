const CRICKET_COUNTRY_CODES = {
  Afghanistan: "AFG",
  Australia: "AUS",
  Bangladesh: "BAN",
  Canada: "CAN",
  England: "ENG",
  India: "IND",
  Ireland: "IRE",
  Kenya: "KEN",
  Namibia: "NAM",
  Nepal: "NEP",
  Netherlands: "NED",
  "New Zealand": "NZL",
  Oman: "OMA",
  Pakistan: "PAK",
  Scotland: "SCO",
  "South Africa": "RSA",
  "Sri Lanka": "SRI",
  "United Arab Emirates": "UAE",
  USA: "USA",
  "United States": "USA",
  "United States of America": "USA",
  "West Indies": "WI",
  Zimbabwe: "ZIM",
};

/** Returns a familiar cricket abbreviation without mislabelling unknown countries as India. */
export function cricketCountryCode(country, overseas = false) {
  const normalized = String(country || "").trim();
  if (CRICKET_COUNTRY_CODES[normalized]) return CRICKET_COUNTRY_CODES[normalized];
  if (!normalized) return overseas ? "INT" : "IND";

  const words = normalized.split(/\s+/).filter(Boolean);
  if (words.length > 1) return words.map((word) => word[0]).join("").slice(0, 3).toUpperCase();
  return normalized.slice(0, 3).toUpperCase();
}
