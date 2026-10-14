import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export type GroupBy = 'country' | 'department' | 'job-title';

/** Pay figures for one group, in whole US dollars per year. */
export interface PayStats {
  key: string;
  headcount: number;
  total: number;
  average: number;
  median: number;
  min: number;
  max: number;
}

export interface PayInsights {
  currency: string;
  /** The exchange-rate note from the server, shown next to the numbers. */
  assumption: string;
  overall: PayStats;
  groups: PayStats[];
}

@Injectable({ providedIn: 'root' })
export class InsightsService {
  private readonly http = inject(HttpClient);

  /** status: ACTIVE, ON_LEAVE, TERMINATED or ALL. */
  pay(groupBy: GroupBy, status: string): Observable<PayInsights> {
    const params = new HttpParams().set('groupBy', groupBy).set('status', status);
    return this.http.get<PayInsights>('/api/insights/pay', { params });
  }
}
