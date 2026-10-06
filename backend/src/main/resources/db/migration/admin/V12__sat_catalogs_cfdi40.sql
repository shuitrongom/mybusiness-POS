-- =====================================================================
-- MyBusiness Silva — V12 (admin): catálogos oficiales del SAT para CFDI 4.0.
--
-- Precarga los catálogos que el CFDI 4.0 valida y que el módulo de Facturación
-- necesita para autocompletar y validar: régimen fiscal (c_RegimenFiscal),
-- uso del CFDI (c_UsoCFDI), forma de pago (c_FormaPago), método de pago
-- (c_MetodoPago), moneda (c_Moneda). Además amplía c_ClaveUnidad y
-- c_ClaveProdServ con las claves más usadas en el comercio mexicano.
--
-- Son catálogos GLOBALES (viven en el schema admin) y se comparten por todos
-- los negocios. Datos tomados de las guías públicas del SAT (Anexo 20).
-- Idempotente (ON CONFLICT DO NOTHING).
-- =====================================================================

-- ---------------------------------------------------------------------
-- c_RegimenFiscal — régimen fiscal del emisor/receptor
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin.sat_regimen_fiscal (
    clave        VARCHAR(5)   PRIMARY KEY,
    descripcion  VARCHAR(200) NOT NULL,
    fisica       BOOLEAN      NOT NULL DEFAULT TRUE,   -- aplica a persona física
    moral        BOOLEAN      NOT NULL DEFAULT TRUE    -- aplica a persona moral
);
COMMENT ON TABLE admin.sat_regimen_fiscal IS 'Catálogo SAT c_RegimenFiscal (CFDI 4.0).';

INSERT INTO admin.sat_regimen_fiscal (clave, descripcion, fisica, moral) VALUES
    ('601', 'General de Ley Personas Morales',                             FALSE, TRUE),
    ('603', 'Personas Morales con Fines no Lucrativos',                    FALSE, TRUE),
    ('605', 'Sueldos y Salarios e Ingresos Asimilados a Salarios',        TRUE,  FALSE),
    ('606', 'Arrendamiento',                                               TRUE,  FALSE),
    ('607', 'Régimen de Enajenación o Adquisición de Bienes',             TRUE,  FALSE),
    ('608', 'Demás ingresos',                                              TRUE,  FALSE),
    ('610', 'Residentes en el Extranjero sin Establecimiento en México',  TRUE,  TRUE),
    ('611', 'Ingresos por Dividendos (socios y accionistas)',             TRUE,  FALSE),
    ('612', 'Personas Físicas con Actividades Empresariales y Profesionales', TRUE, FALSE),
    ('614', 'Ingresos por intereses',                                      TRUE,  FALSE),
    ('615', 'Régimen de los ingresos por obtención de premios',           TRUE,  FALSE),
    ('616', 'Sin obligaciones fiscales',                                   TRUE,  FALSE),
    ('620', 'Sociedades Cooperativas de Producción que optan por diferir sus ingresos', FALSE, TRUE),
    ('621', 'Incorporación Fiscal',                                        TRUE,  FALSE),
    ('622', 'Actividades Agrícolas, Ganaderas, Silvícolas y Pesqueras',   FALSE, TRUE),
    ('623', 'Opcional para Grupos de Sociedades',                          FALSE, TRUE),
    ('624', 'Coordinados',                                                 FALSE, TRUE),
    ('625', 'Régimen de las Actividades Empresariales con ingresos a través de Plataformas Tecnológicas', TRUE, FALSE),
    ('626', 'Régimen Simplificado de Confianza',                           TRUE,  TRUE)
ON CONFLICT (clave) DO NOTHING;

-- ---------------------------------------------------------------------
-- c_UsoCFDI — uso que el receptor dará al comprobante
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin.sat_uso_cfdi (
    clave        VARCHAR(5)   PRIMARY KEY,
    descripcion  VARCHAR(200) NOT NULL,
    fisica       BOOLEAN      NOT NULL DEFAULT TRUE,
    moral        BOOLEAN      NOT NULL DEFAULT TRUE
);
COMMENT ON TABLE admin.sat_uso_cfdi IS 'Catálogo SAT c_UsoCFDI (CFDI 4.0).';

INSERT INTO admin.sat_uso_cfdi (clave, descripcion, fisica, moral) VALUES
    ('G01', 'Adquisición de mercancías',                                   TRUE,  TRUE),
    ('G02', 'Devoluciones, descuentos o bonificaciones',                   TRUE,  TRUE),
    ('G03', 'Gastos en general',                                           TRUE,  TRUE),
    ('I01', 'Construcciones',                                              TRUE,  TRUE),
    ('I02', 'Mobiliario y equipo de oficina por inversiones',             TRUE,  TRUE),
    ('I03', 'Equipo de transporte',                                        TRUE,  TRUE),
    ('I04', 'Equipo de cómputo y accesorios',                             TRUE,  TRUE),
    ('I05', 'Dados, troqueles, moldes, matrices y herramental',           TRUE,  TRUE),
    ('I06', 'Comunicaciones telefónicas',                                  TRUE,  TRUE),
    ('I07', 'Comunicaciones satelitales',                                  TRUE,  TRUE),
    ('I08', 'Otra maquinaria y equipo',                                    TRUE,  TRUE),
    ('D01', 'Honorarios médicos, dentales y gastos hospitalarios',        TRUE,  FALSE),
    ('D02', 'Gastos médicos por incapacidad o discapacidad',              TRUE,  FALSE),
    ('D03', 'Gastos funerales',                                            TRUE,  FALSE),
    ('D04', 'Donativos',                                                   TRUE,  FALSE),
    ('D05', 'Intereses reales pagados por créditos hipotecarios',         TRUE,  FALSE),
    ('D06', 'Aportaciones voluntarias al SAR',                             TRUE,  FALSE),
    ('D07', 'Primas por seguros de gastos médicos',                        TRUE,  FALSE),
    ('D08', 'Gastos de transportación escolar obligatoria',               TRUE,  FALSE),
    ('D09', 'Depósitos en cuentas para el ahorro, primas de pensiones',   TRUE,  FALSE),
    ('D10', 'Pagos por servicios educativos (colegiaturas)',              TRUE,  FALSE),
    ('S01', 'Sin efectos fiscales',                                        TRUE,  TRUE),
    ('CP01', 'Pagos',                                                      TRUE,  TRUE),
    ('CN01', 'Nómina',                                                     TRUE,  FALSE)
ON CONFLICT (clave) DO NOTHING;

-- ---------------------------------------------------------------------
-- c_FormaPago — forma en que se realiza el pago
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin.sat_forma_pago (
    clave        VARCHAR(2)   PRIMARY KEY,
    descripcion  VARCHAR(120) NOT NULL
);
COMMENT ON TABLE admin.sat_forma_pago IS 'Catálogo SAT c_FormaPago (CFDI 4.0).';

INSERT INTO admin.sat_forma_pago (clave, descripcion) VALUES
    ('01', 'Efectivo'),
    ('02', 'Cheque nominativo'),
    ('03', 'Transferencia electrónica de fondos'),
    ('04', 'Tarjeta de crédito'),
    ('05', 'Monedero electrónico'),
    ('06', 'Dinero electrónico'),
    ('08', 'Vales de despensa'),
    ('12', 'Dación en pago'),
    ('13', 'Pago por subrogación'),
    ('14', 'Pago por consignación'),
    ('15', 'Condonación'),
    ('17', 'Compensación'),
    ('23', 'Novación'),
    ('24', 'Confusión'),
    ('25', 'Remisión de deuda'),
    ('26', 'Prescripción o caducidad'),
    ('27', 'A satisfacción del acreedor'),
    ('28', 'Tarjeta de débito'),
    ('29', 'Tarjeta de servicios'),
    ('30', 'Aplicación de anticipos'),
    ('31', 'Intermediario pagos'),
    ('99', 'Por definir')
ON CONFLICT (clave) DO NOTHING;

-- ---------------------------------------------------------------------
-- c_MetodoPago — método de pago (una exhibición / parcialidades)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin.sat_metodo_pago (
    clave        VARCHAR(3)   PRIMARY KEY,
    descripcion  VARCHAR(120) NOT NULL
);
COMMENT ON TABLE admin.sat_metodo_pago IS 'Catálogo SAT c_MetodoPago (CFDI 4.0).';

INSERT INTO admin.sat_metodo_pago (clave, descripcion) VALUES
    ('PUE', 'Pago en una sola exhibición'),
    ('PPD', 'Pago en parcialidades o diferido')
ON CONFLICT (clave) DO NOTHING;

-- ---------------------------------------------------------------------
-- c_Moneda — subconjunto usado en México
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin.sat_moneda (
    clave        VARCHAR(3)   PRIMARY KEY,
    descripcion  VARCHAR(120) NOT NULL
);
COMMENT ON TABLE admin.sat_moneda IS 'Catálogo SAT c_Moneda (subconjunto).';

INSERT INTO admin.sat_moneda (clave, descripcion) VALUES
    ('MXN', 'Peso Mexicano'),
    ('USD', 'Dólar americano'),
    ('EUR', 'Euro'),
    ('XXX', 'Los códigos asignados para las transacciones donde intervenga ninguna moneda')
ON CONFLICT (clave) DO NOTHING;

-- ---------------------------------------------------------------------
-- Ampliación de c_ClaveUnidad con las unidades más usadas en comercio
-- ---------------------------------------------------------------------
INSERT INTO admin.sat_unit (clave, nombre) VALUES
    ('H87', 'Pieza'),
    ('EA',  'Elemento'),
    ('KGM', 'Kilogramo'),
    ('GRM', 'Gramo'),
    ('LTR', 'Litro'),
    ('MLT', 'Mililitro'),
    ('MTR', 'Metro'),
    ('CMT', 'Centímetro'),
    ('MTK', 'Metro cuadrado'),
    ('MTQ', 'Metro cúbico'),
    ('XBX', 'Caja'),
    ('XPK', 'Paquete'),
    ('XUN', 'Unidad'),
    ('E48', 'Unidad de servicio'),
    ('ACT', 'Actividad'),
    ('HUR', 'Hora'),
    ('DAY', 'Día'),
    ('MON', 'Mes'),
    ('ANN', 'Año'),
    ('SET', 'Conjunto'),
    ('PR',  'Par'),
    ('DPC', 'Docena de piezas'),
    ('KT',  'Kit'),
    ('XLT', 'Lote'),
    ('C62', 'Uno'),
    ('E51', 'Trabajo'),
    ('A9',  'Tarifa'),
    ('GLL', 'Galón'),
    ('TNE', 'Tonelada'),
    ('BG',  'Bolsa'),
    ('XBG', 'Bolsa'),
    ('BO',  'Botella'),
    ('XBO', 'Botella'),
    ('CS',  'Estuche'),
    ('RO',  'Rollo'),
    ('XRO', 'Rollo')
ON CONFLICT (clave) DO NOTHING;

-- ---------------------------------------------------------------------
-- Ampliación de c_ClaveProdServ con claves usuales del comercio MX
-- (claves de clase terminadas en 00, agrupadores válidos del SAT)
-- ---------------------------------------------------------------------
INSERT INTO admin.sat_prod_serv (clave, descripcion) VALUES
    ('01010101', 'No existe en el catálogo'),
    ('50110000', 'Carne y aves de corral'),
    ('50120000', 'Pescados y mariscos'),
    ('50130000', 'Productos lácteos y huevos'),
    ('50180000', 'Productos de panadería'),
    ('50181900', 'Pan y galletas'),
    ('50190000', 'Alimentos preparados y conservados'),
    ('50200000', 'Bebidas'),
    ('50202200', 'Bebidas no alcohólicas'),
    ('50202201', 'Agua'),
    ('50202306', 'Refrescos'),
    ('50441800', 'Frutas'),
    ('50450000', 'Verduras frescas'),
    ('50300000', 'Frutas, verduras y frutos secos'),
    ('90101500', 'Servicio de comida y bebida (restaurante)'),
    ('90101501', 'Servicios de banquetes'),
    ('53100000', 'Ropa'),
    ('53110000', 'Prendas de vestir'),
    ('53111600', 'Camisas y blusas'),
    ('53101500', 'Ropa exterior'),
    ('53111500', 'Ropa interior'),
    ('53120000', 'Calzado'),
    ('53121600', 'Calzado deportivo'),
    ('52140000', 'Aparatos electrodomésticos'),
    ('43210000', 'Equipo informático (computadoras)'),
    ('43211500', 'Computadoras'),
    ('43211900', 'Accesorios de cómputo'),
    ('43220000', 'Equipo de telecomunicaciones'),
    ('43191500', 'Teléfonos celulares'),
    ('52160000', 'Equipo de audio y video'),
    ('44120000', 'Papelería y útiles de oficina'),
    ('14110000', 'Papel'),
    ('31160000', 'Ferretería (tornillería y herrajes)'),
    ('40140000', 'Plomería (tubería y accesorios)'),
    ('39120000', 'Material eléctrico'),
    ('30100000', 'Materiales de construcción'),
    ('12350000', 'Pinturas y recubrimientos'),
    ('51100000', 'Medicamentos'),
    ('51470000', 'Productos farmacéuticos de venta libre'),
    ('42140000', 'Productos e instrumentos médicos'),
    ('53131600', 'Productos de cuidado del cabello y baño'),
    ('53131500', 'Productos de cuidado personal'),
    ('50161500', 'Chocolate y azúcares'),
    ('50192700', 'Dulces y chocolates'),
    ('47131800', 'Productos de limpieza'),
    ('56100000', 'Muebles'),
    ('27110000', 'Herramientas manuales'),
    ('25170000', 'Autopartes y accesorios'),
    ('15100000', 'Combustibles (gasolina, diésel, gas)'),
    ('78180000', 'Servicios de transporte de carga por carretera'),
    ('80100000', 'Servicios de asesoría gerencial'),
    ('80110000', 'Servicios de recursos humanos'),
    ('81110000', 'Servicios informáticos'),
    ('82100000', 'Servicios de publicidad'),
    ('84110000', 'Servicios contables y de auditoría'),
    ('85120000', 'Servicios médicos'),
    ('86100000', 'Servicios educativos'),
    ('72100000', 'Servicios de mantenimiento y reparación'),
    ('76110000', 'Servicios de limpieza de inmuebles'),
    ('80140000', 'Servicios de comercialización y ventas'),
    ('93140000', 'Servicios comunitarios y sociales')
ON CONFLICT (clave) DO NOTHING;
