from eduescale.gui.models.school_map import SchoolMapAccumulator


def _record(**overrides):
    record = {
        "nombre": "Colegio Ejemplo",
        "direccion": "Av. Principal 123",
        "localidad": "Localidad",
        "cp": "Centro poblado",
        "turno": "Mañana",
        "alumnos": 10,
        "secciones": 2,
        "longitud": None,
        "latitud": None,
        "distrito": "Distrito",
        "provincia": "Provincia",
        "region": "Departamento",
        "gestion": "publica",
        "nivel": "primaria",
        "codigo_local": "000123",
    }
    return {**record, **overrides}


def test_groups_school_records_and_waits_for_valid_coordinates():
    accumulator = SchoolMapAccumulator()

    assert accumulator.add_batch([_record()]) == []

    schools = accumulator.add_batch(
        [
            _record(
                turno="Tarde",
                alumnos=20,
                secciones=3,
                longitud=-77.2,
                latitud=-12.1,
                nivel="secundaria",
            )
        ]
    )

    assert len(schools) == 1
    school = schools[0]
    assert school["codigoLocal"] == "000123"
    assert school["niveles"] == ["primaria", "secundaria"]
    assert school["turnos"] == ["Mañana", "Tarde"]
    assert school["alumnos"] == 30
    assert school["secciones"] == 5
    assert school["ofertaEducativa"] == [
        {
            "nivel": "primaria",
            "turno": "Mañana",
            "alumnos": 10,
            "secciones": 2,
        },
        {
            "nivel": "secundaria",
            "turno": "Tarde",
            "alumnos": 20,
            "secciones": 3,
        },
    ]


def test_invalid_coordinates_do_not_create_map_locations():
    accumulator = SchoolMapAccumulator()

    schools = accumulator.add_batch([_record(longitud=-77.2, latitud=91)])

    assert schools == []
