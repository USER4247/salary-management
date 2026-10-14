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

describe('EmployeeService writes', () => {
  const input = {
    fullName: 'A B', email: 'a@b.co', country: 'IN', department: 'Finance', jobTitle: 'Accountant',
    annualSalary: 1, currency: 'INR', hireDate: '2024-01-01', status: 'ACTIVE',
  };

  it('creates, updates and deletes through the matching endpoints', () => {
    TestBed.configureTestingModule({ providers: authTestProviders() });
    const service = TestBed.inject(EmployeeService);
    const backend = TestBed.inject(HttpTestingController);

    service.create(input).subscribe();
    const create = backend.expectOne('/api/employees');
    expect([create.request.method, create.request.body]).toEqual(['POST', input]);
    create.flush({});

    service.update(7, input).subscribe();
    expect(backend.expectOne('/api/employees/7').request.method).toBe('PUT');

    service.remove(7).subscribe();
    expect(backend.expectOne('/api/employees/7').request.method).toBe('DELETE');
  });
});
