package ar.com.hexium.hcop.admin.application.port.out;

/** Traduce el `unique` de {@code local_users} sobre username/email — carrera entre el chequeo y el insert. */
public final class UsernameOrEmailConflictException extends RuntimeException {
}
