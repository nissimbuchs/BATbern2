export interface ISession {
    bat: number;
    pdf: string;
    author: string;
    title: string;
    abstract: string;
    referenten: IReferent[];
  }
  
export interface IReferent {
  name: string;
  bio: string;
  portrait: string;
  company: string;
}  