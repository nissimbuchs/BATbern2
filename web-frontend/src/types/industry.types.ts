export interface IndustryName {
  en: string;
  de: string;
}

export interface Industry {
  id: string;
  name: IndustryName;
}

export interface IndustriesData {
  industries: Industry[];
  meta: {
    totalIndustries: number;
    lastUpdated: string;
  };
}
