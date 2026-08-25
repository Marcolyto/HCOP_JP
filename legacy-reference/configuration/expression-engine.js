(function (root) {
  "use strict";

  const FUNCTIONS = Object.freeze({
    abs: Math.abs,
    sqrt: Math.sqrt,
    round: Math.round,
    floor: Math.floor,
    ceil: Math.ceil,
    min: Math.min,
    max: Math.max,
    pow: Math.pow,
    log: Math.log,
    exp: Math.exp,
  });

  function tokenize(source) {
    const tokens = [];
    let index = 0;
    while (index < source.length) {
      const char = source[index];
      if (/\s/.test(char)) { index += 1; continue; }
      const number = source.slice(index).match(/^(?:\d+(?:\.\d*)?|\.\d+)(?:e[+-]?\d+)?/i);
      if (number) { tokens.push({ type: "number", value: Number(number[0]) }); index += number[0].length; continue; }
      const identifier = source.slice(index).match(/^[A-Za-z_][A-Za-z0-9_]*/);
      if (identifier) { tokens.push({ type: "identifier", value: identifier[0] }); index += identifier[0].length; continue; }
      if ("+-*/%^(),".includes(char)) { tokens.push({ type: char, value: char }); index += 1; continue; }
      throw new Error(`Caracter no permitido en la formula: ${char}`);
    }
    tokens.push({ type: "end" });
    return tokens;
  }

  function evaluate(expression, variables = {}) {
    const tokens = tokenize(String(expression || ""));
    let position = 0;
    const peek = () => tokens[position];
    const consume = (type) => {
      const token = peek();
      if (token.type !== type) throw new Error(`Se esperaba ${type}.`);
      position += 1;
      return token;
    };
    const numeric = (value, label) => {
      const result = typeof value === "boolean" ? (value ? 1 : 0) : Number(value);
      if (!Number.isFinite(result)) throw new Error(`${label} no tiene un valor numerico valido.`);
      return result;
    };
    function primary() {
      if (peek().type === "number") return consume("number").value;
      if (peek().type === "identifier") {
        const name = consume("identifier").value;
        if (peek().type === "(") {
          consume("(");
          const args = [];
          if (peek().type !== ")") {
            do { args.push(additive()); } while (peek().type === "," && consume(","));
          }
          consume(")");
          const fn = FUNCTIONS[name.toLowerCase()];
          if (!fn) throw new Error(`Funcion no permitida: ${name}.`);
          return numeric(fn(...args), name);
        }
        if (!Object.hasOwn(variables, name)) throw new Error(`Falta la variable ${name}.`);
        return numeric(variables[name], name);
      }
      if (peek().type === "(") { consume("("); const value = additive(); consume(")"); return value; }
      throw new Error("La formula esta incompleta.");
    }
    function unary() {
      if (peek().type === "+") { consume("+"); return unary(); }
      if (peek().type === "-") { consume("-"); return -unary(); }
      return primary();
    }
    function power() {
      let value = unary();
      if (peek().type === "^") { consume("^"); value = Math.pow(value, power()); }
      return value;
    }
    function multiplicative() {
      let value = power();
      while (["*", "/", "%"].includes(peek().type)) {
        const operator = peek().type; position += 1; const right = power();
        value = operator === "*" ? value * right : operator === "/" ? value / right : value % right;
      }
      return value;
    }
    function additive() {
      let value = multiplicative();
      while (["+", "-"].includes(peek().type)) {
        const operator = peek().type; position += 1; const right = multiplicative();
        value = operator === "+" ? value + right : value - right;
      }
      return value;
    }
    const result = additive();
    if (peek().type !== "end") throw new Error("La formula contiene elementos no reconocidos.");
    if (!Number.isFinite(result)) throw new Error("El resultado no es un numero finito.");
    return result;
  }

  root.SafeExpression = Object.freeze({ evaluate, functions: Object.keys(FUNCTIONS) });
})(window);
