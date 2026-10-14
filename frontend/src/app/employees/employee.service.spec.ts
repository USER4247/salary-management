import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { authTestProviders } from '../core/testing';
import { EmployeeQuery, EmployeeService } from './employee.service';

const BASE: EmployeeQuery = {
  page: 0, size: 25, sort: 'fullName', direction: 'asc', search: '', country: '', department: '', status: '',
};

describe('EmployeeService', () => {
  function setup() {
    TestBed.configureTestingModule({ providers: authTestProviders() });
    return { service: TestBed.inject(EmployeeService), backend: TestBed.inject(HttpTestingController) };
  }

  it('asks for the page, size and sort, and leaves out filters that are empty', () => {
    const { service, backend } = setup();
    service.list(BASE).subscribe();

    const request = backend.expectOne((r) => r.url === '/api/employees');
    expect(request.request.params.get('page')).toBe('0');
    expect(request.request.params.get('size')).toBe('25');
    expect(request.request.params.get('sort')).toBe('fullName,asc');
    expect(request.request.params.has('search')).toBe(false);
    expect(request.request.params.has('country')).toBe(false);
    request.flush({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
  });

  it('sends the filters that are filled in, trimmed', () => {
    const { service, backend } = setup();
    service.list({ ...BASE, search: '  asha ', country: 'IN', status: 'ACTIVE', direction: 'desc', sort: 'annualSalary' }).subscribe();

    const request = backend.expectOne((r) => r.url === '/api/employees');
    expect(request.request.params.get('search')).toBe('asha');
    expect(request.request.params.get('country')).toBe('IN');
    expect(request.request.params.get('status')).toBe('ACTIVE');
    expect(request.request.params.get('sort')).toBe('annualSalary,desc');
    request.flush({ content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 });
  });
});
