import {Mix, Selectable, Selection} from "entcore-toolkit";
import {idiom, notify} from "entcore";
import {schoolService} from "../services";

export class School implements Selectable {
    owner: { userId: string; displayName: string };

    id: number;
    idneo: string;
    uai: string;
    nom: string;
    principal: boolean;
    id_principal: number;
    selected: boolean;

    // Connexion au serveur PMB de cet établissement (vide si pas encore configurée, ou si
    // l'établissement est secondaire et hérite de la connexion de son établissement principal).
    pmb_host: string;
    pmb_endpoint: string;
    pmb_source_id: string;
    pmb_username: string;
    pmb_password: string;
    pmb_page_size: number;

    constructor() {
        this.id = null;
        this.idneo = null;
        this.uai = null;
        this.nom = null;
        this.principal = null;
        this.id_principal = null;
        this.selected = false;
        this.pmb_host = null;
        this.pmb_endpoint = null;
        this.pmb_source_id = null;
        this.pmb_username = null;
        this.pmb_password = null;
        this.pmb_page_size = null;
    }

    // La connexion est considérée configurée dès que l'adresse du serveur est renseignée.
    isConnectionConfigured() : boolean {
        return !!this.pmb_host;
    }

    toJson() : Object {
        return {
            id: this.id,
            idneo: this.idneo,
            uai: this.uai,
            nom: this.nom,
            principal: this.principal,
            id_principal: this.id_principal,
        }
    }
}

export class Schools extends Selection<School> {
    all: School[];

    constructor() {
        super([]);
    }

    sync = async () : Promise<void> => {
        this.all = [];
        try {
            let { data } = await schoolService.list();
            this.all = Mix.castArrayAs(School, data);
        } catch (e) {
            notify.error(idiom.translate('pmb.error.schools.sync'));
            throw e;
        }
    };

    syncNeo = async () : Promise<void> => {
        this.all = [];
        try {
            let { data } = await schoolService.listNeo();
            this.all = Mix.castArrayAs(School, data);
        } catch (e) {
            notify.error(idiom.translate('pmb.error.schools.sync'));
            throw e;
        }
    };
}