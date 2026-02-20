<h2><?php _z('Customize titles'); ?></h2>
<p><?php _z('Configure the titles that are generated in importers'); ?>.</p>
<hr>
<table class="form-table dbmv">
    <tbody>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <label for="dbmv-input-titlemovies"><?php _z('Movies'); ?></label>
            </th>
            <td>
                <?php $this->field_text('titlemovies'); ?>
                <p><strong><?php _z('Usable tags'); ?>:</strong> <code>{name}</code> <code>{year}</code></p>
            </td>
        </tr>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <label for="dbmv-input-titletvshows"><?php _z('TVShows'); ?></label>
            </th>
            <td>
                <?php $this->field_text('titletvshows'); ?>
                <p><strong><?php _z('Usable tags'); ?>:</strong> <code>{name}</code> <code>{year}</code></p>
            </td>
        </tr>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <label for="dbmv-input-titleseasons"><?php _z('Seasons'); ?></label>
            </th>
            <td>
                <?php $this->field_text('titleseasons'); ?>
                <p><strong><?php _z('Usable tags'); ?>:</strong> <code>{name}</code> <code>{season}</code></p>
            </td>
        </tr>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <label for="dbmv-input-titlepisodes"><?php _z('Episodes'); ?></label>
            </th>
            <td>
                <?php $this->field_text('titlepisodes'); ?>
                <p><strong><?php _z('Usable tags'); ?>:</strong> <code>{name}</code> <code>{season}</code> <code>{episode}</code></p>
            </td>
        </tr>
    </tbody>
</table>
<hr>
<h2><?php _z('Customize Content'); ?></h2>
<p><?php _z('Customize how content for movies and tvshows will be imported'); ?>.</p>
<table class="form-table dbmv">
    <tbody>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <label for="dbmv-textarea-composer-content-movies"><?php _z('Content for movies'); ?></label>
            </th>
            <td>
                <?php $this->field_textarea('composer-content-movies','<!-- wp:paragraph -->{synopsis}<!-- /wp:paragraph -->'); ?>
                <p>
                    <strong><?php _z('Usable tags'); ?>:</strong>
                    <code>{year}</code>
                    <code>{synopsis}</code>
                    <code>{title}</code>
                    <code>{title_original}</code>
                </p>
            </td>
        </tr>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <label for="dbmv-textarea-composer-content-tvshows"><?php _z('Content for shows'); ?></label>
            </th>
            <td>
                <?php $this->field_textarea('composer-content-tvshows','<!-- wp:paragraph -->{synopsis}<!-- /wp:paragraph -->'); ?>
                <p>
                    <strong><?php _z('Usable tags'); ?>:</strong>
                    <code>{year}</code>
                    <code>{synopsis}</code>
                    <code>{title}</code>
                    <code>{title_original}</code>
                </p>
            </td>
        </tr>
    </tbody>
</table>
