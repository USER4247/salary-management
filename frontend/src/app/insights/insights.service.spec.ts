import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { authTestProviders } from '../core/testing';
import { InsightsService } from './insights.service';

describe('InsightsService', () => {
  it('asks the server for the chosen grouping and status', () => {
    TestBed.configureTestingModule({ providers: authTestProviders() });
    const backend = TestBed.inject(HttpTestingController);

    TestBed.inject(InsightsService).pay('job-title', 'ALL').subscribe();

    const request = backend.expectOne((r) => r.url === '/api/insights/pay');
    expect(request.request.params.get('groupBy')).toBe('job-title');
    expect(request.request.params.get('status')).toBe('ALL');
    request.flush({});
  });
});
