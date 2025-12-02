package src.main.java;

public class Registro {
    private String tipo;
    private String valor;
    public Registro(String tipo, String valor) {
        this.tipo = tipo;
        this.valor = valor;
    }
    public String getTipo() {
        return tipo;
    }
    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
    public String getValor() {
        return valor;
    }
    public void setValor(String valor) {
        this.valor = valor;
    }
}
