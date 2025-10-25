create table person (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    forename varchar(50) not null,
    surname varchar(50),
    dob date not null
);

insert into person (forename, surname, dob) values ('bob', 'yellow', '2000-01-01');
insert into person (forename, surname, dob) values ('sue', 'green', '1937-04-12');
insert into person (forename, surname, dob) values ('sid', 'orange', '2009-12-05');
insert into person (forename, surname, dob) values ('rob', 'purple', '1990-05-25');
insert into person (forename, surname, dob) values ('cat', 'red', '1981-11-30');