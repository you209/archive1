from app.services.suggestions import infer_from_filename


def test_infer_filename():
    data = infer_from_filename("1998-christmas-family.jpg")
    assert data["year"][0] == "1998"
    assert data["event"][0] == "Christmas"
