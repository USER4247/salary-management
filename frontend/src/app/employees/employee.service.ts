import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface Employee {
  id: number;
  fullName: string;
  email: string;
  country: string;
  department: string;
  jobTitle: string;
  annualSalary: number;
  currency: string;
  hireDate: string;
  status: string;
}

/** One page of employees, as sent by the server. `page` starts at 0. */
export interface EmployeePage {
  content: Employee[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** What the list screen asks for. Empty text means "do not filter on this". */
export interface EmployeeQuery {
  page: number;
  size: number;
  sort: string;                 // column, e.g. "annualSalary"
  direction: 'asc' | 'desc';
  search: string;
  country: string;
  department: string;
  status: string;
}

/** What the add and edit form sends (everything except the id). */
export interface EmployeeInput {
  fullName: string;
  email: string;
  country: string;
  department: string;
  jobTitle: string;
  annualSalary: number;
  currency: string;
  hireDate: string;   // yyyy-MM-dd
  status: string;
}

@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private readonly http = inject(HttpClient);

  create(input: EmployeeInput): Observable<Employee> {
    return this.http.post<Employee>('/api/employees', input);
  }

  update(id: number, input: EmployeeInput): Observable<Employee> {
    return this.http.put<Employee>(`/api/employees/${id}`, input);
  }

  remove(id: number): Observable<void> {
    return this.http.delete<void>(`/api/employees/${id}`);
  }

  list(query: EmployeeQuery): Observable<EmployeePage> {
    let params = new HttpParams()
      .set('page', query.page)
      .set('size', query.size)
      .set('sort', `${query.sort},${query.direction}`);

    // Only send the filters that are actually used.
    const optional = { search: query.search, country: query.country, department: query.department, status: query.status };
    for (const [name, value] of Object.entries(optional)) {
      if (value.trim()) {
        params = params.set(name, value.trim());
      }
    }
    return this.http.get<EmployeePage>('/api/employees', { params });
  }
}
