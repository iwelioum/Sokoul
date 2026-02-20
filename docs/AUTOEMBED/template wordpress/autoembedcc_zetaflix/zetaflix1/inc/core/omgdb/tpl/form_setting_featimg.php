<h2><?php _z('Customize Featured Image'); ?></h2>
<p><?php _z('Configure the images that are generated and imported as featured image'); ?>.</p>
<hr>
<table class="form-table dbmv">
    <tbody>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <?php _z('Import Image'); 
				?>
            </th>			
            <td>
                <?php $this->field_checkbox('upload', __z('Upload image to the server as Featured Image')); ?>
            </td>
        </tr>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <label for="poster-size"><?php _z('Poster Size'); ?></label>
            </th>
            <td>
                <?php $this->field_select('poster-size', self::PosterSize(), 'w780'); ?>
            </td>
        </tr>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <label for="backdrop-size"><?php _z('Backdrop Size'); ?></label>
            </th>
            <td>
                <?php $this->field_select('backdrop-size', self::BackdropSize(), 'w1280'); ?>
            </td>
        </tr>
    </tbody>
</table>
<hr>
<h2>Image Source</h2>
<table class="form-table dbmv">
    <tbody>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <label for="poster-source"><?php _z('Movies'); ?></label>
            </th>
            <td>
            <?php $this->field_radio('poster-source', self::PosterSource(), 'pstr' ); ?>
            </td>
        </tr>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <label for="poster-source-tv"><?php _z('TV Shows'); ?></label>
            </th>
            <td>
            <?php $this->field_radio('poster-source-tv', self::PosterSource(), 'pstr' ); ?>
            </td>
        </tr>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <label for="poster-source-ss"><?php _z('Seasons'); ?></label>
            </th>
            <td>
            <?php $this->field_radio('poster-source-ss ', array('pstr' => __z('Poster Image')), 'pstr' ); ?>
            </td>
        </tr>
        <tr valign="top">
            <th scope="row" class="titledesc">
                <label for="poster-source-ep"><?php _z('Episodes'); ?></label>
            </th>
            <td>
            <?php $this->field_radio('poster-source-ep', array('bckdrp' => __z('Backdrop Image')), 'bckdrp' ); ?>
            </td>
        </tr>
    </tbody>
</table>
