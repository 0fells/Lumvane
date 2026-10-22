package clashapi

import (
	"net/http"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/render"
)

func scriptRouter() http.Handler {
	r := chi.NewRouter()
	r.Post("/", testScript)
	r.Patch("/", patchScript)
	return r
}

func testScript(w http.ResponseWriter, r *http.Request) {
	render.Status(r, http.StatusBadRequest)
	render.JSON(w, r, newError("not implemented"))
}

type PatchScriptRequest struct {
	Script string `json:"script"`
}

func patchScript(w http.ResponseWriter, r *http.Request) {
	render.NoContent(w, r)
}
