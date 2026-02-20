<h2><?php _z('Metadata Updater'); ?></h2>
<p><?php _z('This tool updates and repairs metadata of all content published or imported by Omegadb'); ?>.</p>
<hr>
<table class="form-table dbmv">
    <tbody>
        <tr valign="top">
            <th scope="row" class="titledesc"><?php _z('Method'); ?></th>
            <td>
                <?php $this->field_radio('updatermethod', self::UpdaterMethod(),'wp-ajax'); ?>
            </td>
        </tr>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <?php _z('Post Types'); ?>
            </th>
            <td>
                <?php $this->field_checkbox('updatermovies', __z('Movies')); ?>
                <?php $this->field_checkbox('updatershows', __z('TV Shows')); ?>
                <?php $this->field_checkbox('updaterseasons', __z('TV Shows > Seasons')); ?>
                <?php $this->field_checkbox('updaterepisodes', __z('TV Shows > Episodes')); ?>
            </td>
        </tr>
    </tbody>
</table>
